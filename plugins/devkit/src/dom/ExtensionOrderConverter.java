// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.references.PomService;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.ReferenceSetBase;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.util.ExtensionCandidate;
import org.jetbrains.idea.devkit.util.ExtensionLocator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ExtensionOrderConverter implements CustomReferenceConverter<String> {
  private static final Logger LOG = Logger.getInstance(ExtensionOrderConverter.class);

  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<String> value, PsiElement element, ConvertContext context) {
    PsiElement originalElement = CompletionUtil.getOriginalOrSelf(element); // avoid 'IntellijIdeaRulezzz' placeholder
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
          return Collections.emptyList();
        }

        String idSubPart = null; // second one, after keyword subpart
        if (subParts.size() == 2) {
          idSubPart = subParts.get(1);
        }
        else if (isBeforeOrAfterKeyword(StringUtil.trimLeading(orderPart), false)) {
          // This order part is, for instance, 'after ' or 'before:'. In such cases IDs reference should be provided.
          idSubPart = "";
        }

        if (subParts.size() > 2 || (idSubPart != null && !isBeforeOrAfterKeyword(subParts.get(0)))) {
          // Order value can't contain more than 3 subparts. If there are two subparts, first one must be 'before' or 'after'.
          return Collections.singletonList(new InvalidOrderPartPsiReference(getElement(), range, orderPart));
        }

        List<TextRange> wordIndices = getWordIndicesInOrderPart(orderPart);
        if (wordIndices.isEmpty()) {
          LOG.error("Unexpected empty word indices list for 'order' part: " + orderPart);
          return Collections.singletonList(new InvalidOrderPartPsiReference(getElement(), range, orderPart));
        }
        if (idSubPart != null && idSubPart.isEmpty()) { // right after the before/after keyword
          wordIndices.add(new TextRange(orderPart.length(), orderPart.length()));
        }

        if (idSubPart == null) {
          return Collections.emptyList();
        }
        assert wordIndices.size() == 2 : wordIndices.toString();
        TextRange idSubPartRange = wordIndices.get(1).shiftRight(range.getStartOffset());
        return ContainerUtil.list(new OrderReferencedIdPsiReference(getElement(), idSubPartRange, idSubPart, extension));
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

  private static boolean isBeforeOrAfterKeyword(String str) {
    return isBeforeOrAfterKeyword(str, true);
  }

  private static boolean isBeforeOrAfterKeyword(String str, boolean trimKeyword) {
    return (trimKeyword ? LoadingOrder.BEFORE_STR.trim() : LoadingOrder.BEFORE_STR).equalsIgnoreCase(str) ||
           (trimKeyword ? LoadingOrder.AFTER_STR.trim(): LoadingOrder.AFTER_STR).equalsIgnoreCase(str) ||
           LoadingOrder.BEFORE_STR_OLD.equalsIgnoreCase(str) ||
           LoadingOrder.BEFORE_STR_OLD.equalsIgnoreCase(str);
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
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @NotNull
    @Override
    public String getUnresolvedMessagePattern() {
      return DevKitBundle.message("invalid.order.attribute.part", myOrderPart.trim());
    }

    @Override
    public boolean isSoft() {
      return true;
    }
  }

  private static class OrderReferencedIdPsiReference extends PsiReferenceBase<PsiElement> implements EmptyResolveMessageProvider {
    private final String myReferencedId;
    private final Extension myExtension;

    public OrderReferencedIdPsiReference(@NotNull PsiElement element, @NotNull TextRange rangeInElement,
                                         @NotNull String referencedId, @NotNull Extension extension) {
      super(element, rangeInElement);
      myReferencedId = referencedId;
      myExtension = extension;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      ExtensionPoint extensionPoint = myExtension.getExtensionPoint();
      if (extensionPoint == null) {
        return null;
      }

      ExtensionLocator epAndIdLocator = ExtensionLocator.byExtensionPointAndId(extensionPoint, myReferencedId);
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
      ExtensionPoint extensionPoint = myExtension.getExtensionPoint();
      if (extensionPoint == null) {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }

      ExtensionLocator epLocator = ExtensionLocator.byExtensionPoint(extensionPoint);
      List<ExtensionCandidate> candidates = epLocator.findCandidates();
      Project project = getElement().getProject();
      DomManager domManager = DomManager.getDomManager(project);

      List<Extension> extensionsForThisEp = new ArrayList<>();
      for (ExtensionCandidate candidate : candidates) {
        XmlTag tag = candidate.pointer.getElement();
        DomElement domElement = domManager.getDomElement(tag);
        if (domElement instanceof Extension) {
          extensionsForThisEp.add((Extension)domElement);
        }
      }

      Map<Extension, String> targetExtensionsWithMarks = filterAndMarkExtensions(extensionsForThisEp, project);
      List<LookupElement> idCompletionVariants = getLookupElements(targetExtensionsWithMarks);
      return idCompletionVariants.toArray(new LookupElement[idCompletionVariants.size()]);
    }

    @NotNull
    @Override
    public String getUnresolvedMessagePattern() {
      ExtensionPoint ep = myExtension.getExtensionPoint();
      return "Cannot resolve ''{0}'' " + (ep != null ? ep.getEffectiveName() + " " : "") + "extension";
    }

    @Override
    public boolean isSoft() {
      return true;
    }

    private Map<Extension, String> filterAndMarkExtensions(List<Extension> extensionsForThisEp, Project project) {
      JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
      GlobalSearchScope resolveScope = getElement().getResolveScope();
      PsiClass languageEpClass = javaPsiFacade.findClass(LanguageExtensionPoint.class.getCanonicalName(), resolveScope);
      if (languageEpClass == null) {
        return Collections.emptyMap();
      }
      PsiClass fileTypeEpClass = javaPsiFacade.findClass(FileTypeExtensionPoint.class.getCanonicalName(), resolveScope);
      if (fileTypeEpClass == null) {
        return Collections.emptyMap();
      }

      String currentExtensionId = myExtension.getId().getStringValue();
      String currentExtensionLanguage = getSpecificExtensionAttribute(myExtension, languageEpClass, "language");
      String currentExtensionFileType = getSpecificExtensionAttribute(myExtension, fileTypeEpClass, "filetype");

      Map<Extension, String> result = new HashMap<>();
      for (Extension extension : extensionsForThisEp) {
        String id = extension.getId().getStringValue();
        if (StringUtil.isEmpty(id) || id.equals(currentExtensionId)) {
          continue;
        }

        String extensionMark = null; // to display {language} or {file type}
        if (currentExtensionLanguage != null) {
          String language = getSpecificExtensionAttribute(extension, languageEpClass, "language");
          if (language != null) {
            if (!language.equals("any") && !language.isEmpty() && !language.equals(currentExtensionLanguage)) {
              continue;
            }
            extensionMark = language;
          }
        }
        if (currentExtensionFileType != null) {
          String fileType = getSpecificExtensionAttribute(extension, fileTypeEpClass, "filetype");
          if (fileType != null) {
            if (!currentExtensionFileType.equals(fileType)) {
              continue;
            }
            extensionMark = extensionMark != null ? null : fileType; // null if _somehow_ both filetype and language are present
          }
        }

        result.put(extension, extensionMark);
      }

      return result;
    }

    @NotNull
    private static List<LookupElement> getLookupElements(@NotNull Map<Extension, String> targetExtensionsWithMarks) {
      List<LookupElement> result = new ArrayList<>(targetExtensionsWithMarks.size());
      for (Map.Entry<Extension, String> entry : targetExtensionsWithMarks.entrySet()) {
        Extension extension = entry.getKey();
        String mark = entry.getValue();

        PsiElement targetElement = getTargetElement(extension);
        String id = extension.getId().getStringValue();
        if (StringUtil.isEmpty(id)) {
          LOG.error("Unexpected empty id in target extension: " + extension);
          continue;
        }

        result.add(createLookupElement(targetElement, id, extension.getModule(), mark));
      }
      return result;
    }

    @Nullable
    private static String getSpecificExtensionAttribute(@NotNull Extension e,
                                                        @NotNull PsiClass parentBeanClass,
                                                        @NotNull String attribute) {
      ExtensionPoint ep = e.getExtensionPoint();
      if (ep == null) {
        return null;
      }
      PsiClass beanClass = ep.getBeanClass().getValue();
      if (beanClass == null) {
        return null;
      }
      if (!InheritanceUtil.isInheritorOrSelf(beanClass, parentBeanClass, true)) {
        return null;
      }

      DomAttributeChildDescription attributeDescription = e.getGenericInfo().getAttributeChildDescription(attribute);
      if (attributeDescription == null) {
        return null;
      }
      return attributeDescription.getDomAttributeValue(e).getStringValue();
    }

    @NotNull
    private static PsiElement getTargetElement(Extension e) {
      DomTarget extensionTarget = DomTarget.getTarget(e);
      if (extensionTarget != null) {
        return PomService.convertToPsi(extensionTarget);
      }
      // shouldn't happen, fallback for additional safety
      return e.getXmlTag();
    }

    @NotNull
    private static LookupElement createLookupElement(@NotNull PsiElement targetElement,
                                                     @NotNull String id,
                                                     @Nullable Module module,
                                                     @Nullable String mark) {
      LookupElementBuilder element = LookupElementBuilder.create(targetElement, id);
      if (module != null) {
        element = element.withTypeText(module.getName(), ModuleType.get(module).getIcon(), false).withTypeIconRightAligned(true);
      }
      if (mark != null) {
        element = element.withTailText(" {" + mark + "}", true);
      }
      return element;
    }
  }
}
