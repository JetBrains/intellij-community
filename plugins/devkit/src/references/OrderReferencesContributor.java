// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.util.ExtensionCandidate;
import org.jetbrains.idea.devkit.util.ExtensionLocator;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OrderReferencesContributor extends PsiReferenceContributor {
  private static final LookupElement[] COMPLETION_VARIANTS = new LookupElement[] {
    LookupElementBuilder.create(LoadingOrder.FIRST_STR), LookupElementBuilder.create(LoadingOrder.LAST_STR),
    LookupElementBuilder.create(LoadingOrder.BEFORE_STR), LookupElementBuilder.create(LoadingOrder.AFTER_STR)
  };

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      XmlPatterns.xmlAttributeValue().withLocalName("order"), new PsiReferenceProvider() {
        @NotNull
        @Override
        public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
          if (!PsiUtil.isPluginXmlPsiElement(element)) {
            return PsiReference.EMPTY_ARRAY;
          }

          Extension extension = getExtensionFromAttributeValue(element);
          if (extension == null) {
            return PsiReference.EMPTY_ARRAY;
          }
          String orderValue = extension.getOrder().getStringValue();
          if (orderValue == null) {
            return PsiReference.EMPTY_ARRAY;
          }
          ExtensionPoint extensionPoint = extension.getExtensionPoint();
          if (extensionPoint == null) {
            return PsiReference.EMPTY_ARRAY;
          }

          String[] parts = orderValue.split(LoadingOrder.ORDER_RULE_SEPARATOR);
          if (parts.length == 1 && !orderValue.trim().contains(" ") && !orderValue.trim().contains(":")) {
            // no referenced IDs, just provide completion for keywords
            return new PsiReference[]{new PsiReferenceBase<PsiElement>(element) {
              @Nullable
              @Override
              public PsiElement resolve() {
                return getElement();
              }

              @NotNull
              @Override
              public Object[] getVariants() {
                return COMPLETION_VARIANTS;
              }
            }};
          }

          ExtensionLocator locator = ExtensionLocator.byExtensionPoint(extensionPoint);
          List<ExtensionCandidate> candidates = locator.findCandidates();
          DomManager domManager = DomManager.getDomManager(element.getProject());

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
              idCompletionVariantsList.add(LookupElementBuilder.create(id));
            }
          }
          LookupElement[] idCompletionVariants = idCompletionVariantsList.toArray(new LookupElement[idCompletionVariantsList.size()]);


          PsiReference[] result = new PsiReference[parts.length];
          int processedLength = 0;
          for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String referencedId = getReferencedIdFromOrderPart(part.trim());
            int currentProcessedLength = processedLength;
            result[i] = new OrderPsiReferenceBase(element, extensionPoint.getEffectiveName()) {
              @Nullable
              @Override
              public PsiElement resolve() {
                if (referencedId == null) {
                  return null;
                }

                Optional<Extension> targetExtensionOptional =
                  extensionsForThisEp.stream().filter(e -> referencedId.equals(e.getId().getStringValue())).findAny();
                if (!targetExtensionOptional.isPresent()) {
                  return null;
                }

                XmlTag targetTag = targetExtensionOptional.get().getXmlTag();
                if (targetTag != null) {
                  XmlAttribute idAttr = targetTag.getAttribute("id");
                  if (idAttr != null) {
                    XmlAttributeValue idAttrValue = idAttr.getValueElement();
                    if (idAttrValue != null) {
                      return idAttrValue;
                    }
                    return idAttr;
                  }
                  return targetTag;
                }
                return null;
              }

              @NotNull
              @Override
              public Object[] getVariants() {
                return idCompletionVariants;
              }

              @Override
              protected TextRange calculateDefaultRangeInElement() {
                int prefixLength = referencedId == null ? 0 : getOrderPrefixLength(part);
                assert prefixLength >= 0;
                int currentPartIndex = StringUtil.indexOf(orderValue, part, currentProcessedLength);
                int startOffset = currentPartIndex + prefixLength + 1; // +1 because of quotes surrounding attribute value
                return new TextRange(startOffset, startOffset + part.length() - prefixLength);
              }
            };

            processedLength += parts[i].length() + LoadingOrder.ORDER_RULE_SEPARATOR.length(); // + comma
          }

          return result;
        }
      }, PsiReferenceRegistrar.HIGHER_PRIORITY);
  }

  @Nullable
  private static Extension getExtensionFromAttributeValue(@Nullable PsiElement element) {
    if (!(element instanceof XmlAttributeValue)) {
      return null;
    }
    PsiElement xmlAttribute = element.getParent();
    if (!(xmlAttribute instanceof XmlAttribute)) {
      return null;
    }
    PsiElement xmlTag = xmlAttribute.getParent();
    if (!(xmlTag instanceof XmlTag)) {
      return null;
    }

    DomManager domManager = DomManager.getDomManager(xmlAttribute.getProject());
    DomElement domElement = domManager.getDomElement((XmlTag)xmlTag);
    if (!(domElement instanceof Extension)) {
      return null;
    }
    return (Extension)domElement;
  }

  @Nullable
  private static String getReferencedIdFromOrderPart(@NotNull String orderPart) {
    int prefixLength = getOrderPrefixLength(orderPart);
    if (prefixLength > 0) {
      return orderPart.substring(prefixLength).trim();
    }
    return null;
  }

  private static int getOrderPrefixLength(@NotNull String orderPart) {
    String orderPartWithoutLeadingSpaces = StringUtil.trimLeading(orderPart, ' ');
    int leadingSpacesLength = orderPart.length() - orderPartWithoutLeadingSpaces.length();

    if (StringUtil.startsWithIgnoreCase(orderPartWithoutLeadingSpaces, LoadingOrder.BEFORE_STR)) {
      return leadingSpacesLength + LoadingOrder.BEFORE_STR.length();
    }
    else if (StringUtil.startsWithIgnoreCase(orderPartWithoutLeadingSpaces, LoadingOrder.BEFORE_STR_OLD)) {
      return leadingSpacesLength + LoadingOrder.BEFORE_STR_OLD.length();
    }
    else if (StringUtil.startsWithIgnoreCase(orderPartWithoutLeadingSpaces, LoadingOrder.AFTER_STR)) {
      return leadingSpacesLength + LoadingOrder.AFTER_STR.length();
    }
    else if (StringUtil.startsWithIgnoreCase(orderPartWithoutLeadingSpaces, LoadingOrder.AFTER_STR_OLD)) {
      return leadingSpacesLength + LoadingOrder.AFTER_STR_OLD.length();
    }
    return 0;
  }


  private static abstract class OrderPsiReferenceBase extends PsiReferenceBase<PsiElement> implements EmptyResolveMessageProvider {
    private final String myExtensionPointName;

    public OrderPsiReferenceBase(@NotNull PsiElement element, String extensionPointName) {
      super(element);
      myExtensionPointName = extensionPointName;
    }

    @NotNull
    @Override
    public String getUnresolvedMessagePattern() {
      return "Cannot resolve ''{0}'' " + myExtensionPointName + " extension";
    }

    @Override
    public boolean isSoft() {
      return true;
    }
  }
}
