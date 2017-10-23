// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.codeInsight.completion.AddSpaceInsertHandler;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.references.PomService;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.ReferenceSetBase;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.util.ExtensionCandidate;
import org.jetbrains.idea.devkit.util.ExtensionLocator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExtensionOrderConverter implements CustomReferenceConverter<String> {
  private static final Logger LOG = Logger.getInstance(ExtensionOrderConverter.class);

  private static final LookupElement[] KEYWORD_COMPLETION_VARIANTS = new LookupElement[] {
    LookupElementBuilder.create(LoadingOrder.FIRST_STR), LookupElementBuilder.create(LoadingOrder.LAST_STR),
    LookupElementBuilder.create(LoadingOrder.BEFORE_STR.trim()).withInsertHandler(new AddSpaceInsertHandler(true)),
    LookupElementBuilder.create(LoadingOrder.AFTER_STR.trim()).withInsertHandler(new AddSpaceInsertHandler(true))
  };

  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<String> value, PsiElement element, ConvertContext context) {
    PsiElement originalElement = CompletionUtil.getOriginalOrSelf(element);
    String orderValue = ElementManipulators.getValueText(originalElement);
    if (StringUtil.isEmpty(orderValue)) {
      return PsiReference.EMPTY_ARRAY;
    }
    Extension extension = value.getParentOfType(Extension.class, false);
    if (extension == null) {
      return PsiReference.EMPTY_ARRAY;
    }

    return new ReferenceSetBase<PsiReference>(orderValue, element, 0, ',') {
      @Override
      protected List<PsiReference> createReferences(TextRange range, int index) {
        String orderPart = range.substring(orderValue);

        // reference range and attribute text range are not equal
        range = new TextRange(range.getStartOffset() + 1, range.getEndOffset() + 1);

        List<String> subParts = splitOrderPart(orderPart);
        if (subParts.isEmpty()) {
          // last symbol is ','
          return Collections.singletonList(new OrderKeywordPsiReference(getElement(), range, orderPart));
        }

        String firstSubPart = subParts.get(0);
        String secondSubPart = subParts.size() > 1 ? subParts.get(1) : null;
        if (subParts.size() > 2 || (secondSubPart != null && !isBeforeOrAfterKeyword(firstSubPart))) {
          // Order value can't contain more than 3 subparts. If there are two subparts, first one must be 'before' or 'after'.
          return Collections.singletonList(new InvalidOrderPartPsiReference(getElement(), range, orderPart));
        }

        List<TextRange> wordIndices = getWordIndicesInOrderPart(orderPart);
        if (wordIndices.isEmpty()) {
          LOG.error("Unexpected empty word indices list for 'order' part: " + orderPart);
          return Collections.singletonList(new InvalidOrderPartPsiReference(getElement(), range, orderPart));
        }

        TextRange firstSubPartRange = wordIndices.get(0).shiftRight(range.getStartOffset());
        OrderKeywordPsiReference keywordReference = new OrderKeywordPsiReference(getElement(), firstSubPartRange, firstSubPart);
        if (secondSubPart == null) {
          return Collections.singletonList(keywordReference);
        }
        TextRange secondSubPartRange = wordIndices.get(1).shiftRight(range.getStartOffset());
        return ContainerUtil.list(keywordReference,
                                  new OrderReferencedIdPsiReference(getElement(), secondSubPartRange, secondSubPart, extension));
      }
    }.getPsiReferences();
  }

  private static List<String> splitOrderPart(String orderPart) {
    List<String> result = new ArrayList<>();
    List<String> subParts = StringUtil.split(orderPart, " ");
    subParts.forEach(s -> {
      if (":".equals(s)) {
        result.add(s);
      }
      else {
        Collections.addAll(result, s.split(":"));
      }
    });
    return result;
  }

  private static List<TextRange> getWordIndicesInOrderPart(String orderPart) {
    return StringUtil.getWordIndicesIn(orderPart, ContainerUtil.set(' ', ':'));
  }

  private static boolean isCompleteKeyword(String str) {
    return isFirstOrLastKeyword(str) || isBeforeOrAfterKeyword(str);
  }

  private static boolean isFirstOrLastKeyword(String str) {
    return LoadingOrder.FIRST_STR.equalsIgnoreCase(str) ||
           LoadingOrder.LAST_STR.equalsIgnoreCase(str);
  }

  private static boolean isBeforeOrAfterKeyword(String str) {
    return LoadingOrder.BEFORE_STR.trim().equalsIgnoreCase(str) ||
           LoadingOrder.BEFORE_STR_OLD.trim().equalsIgnoreCase(str) ||
           LoadingOrder.AFTER_STR.trim().equalsIgnoreCase(str) ||
           LoadingOrder.AFTER_STR_OLD.trim().equalsIgnoreCase(str);
  }


  private static class InvalidOrderPartPsiReference extends PsiReferenceBase<PsiElement> implements EmptyResolveMessageProvider {
    private final String myOrderPart;

    public InvalidOrderPartPsiReference(@NotNull PsiElement element, @NotNull TextRange rangeInElement, String orderPart) {
      super(element, rangeInElement);
      myOrderPart = orderPart;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return null;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      return KEYWORD_COMPLETION_VARIANTS;
    }

    @NotNull
    @Override
    public String getUnresolvedMessagePattern() {
      return DevKitBundle.message("invalid.order.attribute.part", myOrderPart);
    }

    @Override
    public boolean isSoft() {
      return true;
    }
  }

  private static class OrderKeywordPsiReference extends PsiReferenceBase<PsiElement> implements EmptyResolveMessageProvider {
    private final String myText;

    public OrderKeywordPsiReference(@NotNull PsiElement element, @NotNull TextRange rangeInElement, @NotNull String text) {
      super(element, rangeInElement);
      myText = text;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return isCompleteKeyword(myText) ? getElement() : null;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      return KEYWORD_COMPLETION_VARIANTS;
    }

    @NotNull
    @Override
    public String getUnresolvedMessagePattern() {
      return DevKitBundle.message("invalid.order.attribute.keyword", myText);
    }

    @Override
    public boolean isSoft() {
      return true;
    }
  }

  private static class OrderReferencedIdPsiReference extends PsiReferenceBase<PsiElement> implements EmptyResolveMessageProvider {
    private final String myReferencedId;
    private final ExtensionPoint myExtensionPoint;


    public OrderReferencedIdPsiReference(@NotNull PsiElement element, @NotNull TextRange rangeInElement,
                                         @NotNull String referencedId, @NotNull Extension extension) {
      super(element, rangeInElement);
      myReferencedId = referencedId;
      myExtensionPoint = extension.getExtensionPoint();
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      if (myExtensionPoint == null) {
        return null;
      }

      ExtensionLocator epAndIdLocator = ExtensionLocator.byExtensionPointAndId(myExtensionPoint, myReferencedId);
      List<ExtensionCandidate> candidates = epAndIdLocator.findCandidates();
      if (candidates.isEmpty()) {
        return null;
      }
      XmlTag referencedElement = candidates.iterator().next().pointer.getElement();
      if (referencedElement == null) {
        return null;
      }

      // return DOM target PSI for "Find Usages" to work
      DomManager domManager = DomManager.getDomManager(referencedElement.getProject());
      DomElement domElement = domManager.getDomElement(referencedElement);
      if (domElement == null) {
        return referencedElement; // fallback
      }
      DomTarget target = DomTarget.getTarget(domElement);
      if (target == null) {
        return referencedElement; // fallback
      }
      return PomService.convertToPsi(target);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      ExtensionLocator epLocator = ExtensionLocator.byExtensionPoint(myExtensionPoint);
      List<ExtensionCandidate> candidates = epLocator.findCandidates();
      DomManager domManager = DomManager.getDomManager(getElement().getProject());

      List<Extension> extensionsForThisEp = new ArrayList<>();
      for (ExtensionCandidate candidate : candidates) {
        XmlTag tag = candidate.pointer.getElement();
        DomElement domElement = domManager.getDomElement(tag);
        if (domElement instanceof Extension) {
          extensionsForThisEp.add((Extension)domElement);
        }
      }

      List<LookupElement> idCompletionVariantsList = new ArrayList<>();
      for (Extension e : extensionsForThisEp) {
        String id = e.getId().getStringValue();
        if (StringUtil.isNotEmpty(id)) {
          DomTarget extensionTarget = DomTarget.getTarget(e);
          if (extensionTarget != null) {
            PsiElement extensionPsi = PomService.convertToPsi(extensionTarget);
            idCompletionVariantsList.add(LookupElementBuilder.create(extensionPsi, id));
          }
          else {
            // shouldn't happen, fallback for additional safety
            idCompletionVariantsList.add(LookupElementBuilder.create(e.getXmlTag(), id));
          }
        }
      }
      return idCompletionVariantsList.toArray(new LookupElement[idCompletionVariantsList.size()]);
    }

    @NotNull
    @Override
    public String getUnresolvedMessagePattern() {
      return "Cannot resolve ''{0}'' " + (myExtensionPoint != null ? myExtensionPoint.getEffectiveName() + " " : "") + "extension";
    }

    @Override
    public boolean isSoft() {
      return true;
    }
  }
}
