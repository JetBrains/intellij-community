// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.IntentionActionBean;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiReferenceProviderBean;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceContributorEP;
import com.intellij.psi.stubs.StubElementTypeHolderEP;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.AddDomElementQuickFix;
import com.intellij.util.xml.highlighting.DefineAttributeQuickFix;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.impl.LanguageResolvingUtil;

public class PluginXmlExtensionRegistrationInspection extends DevKitPluginXmlInspectionBase {

  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    if (!(element instanceof Extension)) {
      return;
    }

    Extension extension = (Extension)element;
    ExtensionPoint extensionPoint = extension.getExtensionPoint();
    if (extensionPoint == null ||
        !DomUtil.hasXml(extensionPoint.getBeanClass())) {
      return;
    }

    if (StubElementTypeHolderEP.class.getName().equals(extensionPoint.getBeanClass().getStringValue())) {
      if (hasMissingAttribute(extension, "externalIdPrefix")) {
        holder.createProblem(extension,
                             DevKitBundle.message("inspection.plugin.xml.extension.registration.should.define.externalidprefix.attribute"),
                             new DefineAttributeQuickFix("externalIdPrefix"));
      }
      return;
    }

    if (ServiceDescriptor.class.getName().equals(extensionPoint.getBeanClass().getStringValue())) {
      GenericAttributeValue<?> serviceInterface = getAttribute(extension, "serviceInterface");
      GenericAttributeValue<?> serviceImplementation = getAttribute(extension, "serviceImplementation");
      if (serviceInterface != null && serviceImplementation != null &&
          StringUtil.equals(serviceInterface.getStringValue(), serviceImplementation.getStringValue())) {
        if (hasMissingAttribute(extension, "testServiceImplementation")) {
          highlightRedundant(serviceInterface,
                             DevKitBundle.message("inspections.plugin.xml.service.interface.class.redundant"),
                             ProblemHighlightType.WARNING, holder);
        }
      }
      return;
    }

    if (InheritanceUtil.isInheritor(extensionPoint.getBeanClass().getValue(), InspectionEP.class.getName())) {
      if (!hasMissingAttribute(element, "key")) {
        if (hasMissingAttribute(element, "bundle")) {
          checkDefaultBundle(element, holder);
        }
      }
      else if (hasMissingAttribute(element, "displayName")) {
        //noinspection DialogTitleCapitalization
        registerDefineAttributeProblem(element, holder,
                                       DevKitBundle.message("inspections.inspection.mapping.consistency.specify.displayName.or.key"),
                                       "displayName", "key");
      }

      if (!hasMissingAttribute(element, "groupKey")) {
        if (hasMissingAttribute(element, "bundle") &&
            hasMissingAttribute(element, "groupBundle")) {
          checkDefaultBundle(element, holder);
        }
      }
      else if (hasMissingAttribute(element, "groupName")) {
        //noinspection DialogTitleCapitalization
        registerDefineAttributeProblem(element, holder,
                                       DevKitBundle.message("inspections.inspection.mapping.consistency.specify.groupName.or.groupKey"),
                                       "groupName", "groupKey");
      }
    }

    if (isExtensionPointWithLanguage(extensionPoint)) {
      DomAttributeChildDescription<?> languageAttributeDescription = extension.getGenericInfo().getAttributeChildDescription("language");
      if (languageAttributeDescription != null) {
        if (!DomUtil.hasXml(languageAttributeDescription.getDomAttributeValue(extension))) {
          holder.createProblem(extension,
                               DevKitBundle.message("inspection.plugin.xml.extension.registration.should.define.language.attribute",
                                                    extensionPoint.getEffectiveQualifiedName()),
                               new DefineAttributeQuickFix("language", "", LanguageResolvingUtil.getAnyLanguageValue(extensionPoint)));
        }
        return;
      }

      // IntentionActionBean, since 223 only
      DomFixedChildDescription languageTagDescription = extension.getGenericInfo().getFixedChildDescription("language");
      if (languageTagDescription != null) {
        GenericDomValue<?> languageTag = getTag(extension, "language");
        if (languageTag != null && !DomUtil.hasXml(languageTag)) {
          holder.createProblem(extension,
                               DevKitBundle.message("inspection.plugin.xml.extension.registration.should.define.language.tag",
                                                    extensionPoint.getEffectiveQualifiedName()),
                               new AddLanguageTagQuickFix(LanguageResolvingUtil.getAnyLanguageValue(extensionPoint)));
        }
      }
    }
  }

  private static void checkDefaultBundle(DomElement element, DomElementAnnotationHolder holder) {
    IdeaPlugin plugin = DomUtil.getParentOfType(element, IdeaPlugin.class, true);
    if (plugin != null && !DomUtil.hasXml(plugin.getResourceBundle())) {
      holder.createProblem(element, DevKitBundle.message("inspections.inspection.mapping.consistency.specify.bundle"),
                           new AddDomElementQuickFix<>(plugin.getResourceBundle()));
    }
  }

  private static void registerDefineAttributeProblem(DomElement element, DomElementAnnotationHolder holder,
                                                     @InspectionMessage String message,
                                                     @NonNls String... attributeNames) {
    if (holder.isOnTheFly()) {
      holder.createProblem(element, message, ContainerUtil.map(attributeNames, attributeName -> {
        return new DefineAttributeQuickFix(attributeName);
      }, new LocalQuickFix[attributeNames.length]));
    }
    else {
      holder.createProblem(element, message);
    }
  }

  private static boolean isExtensionPointWithLanguage(ExtensionPoint extensionPoint) {
    final String extensionBeanClass = extensionPoint.getBeanClass().getStringValue();
    return PsiReferenceContributorEP.class.getName().equals(extensionBeanClass) ||
           PsiReferenceProviderBean.class.getName().equals(extensionBeanClass) ||
           IntentionActionBean.class.getName().equals(extensionBeanClass) ||
           InheritanceUtil.isInheritor(extensionPoint.getBeanClass().getValue(), LanguageExtensionPoint.class.getName());
  }

  private static class AddLanguageTagQuickFix implements LocalQuickFix {

    private final String myAnyLanguageID;

    private AddLanguageTagQuickFix(String anyLanguageId) {
      myAnyLanguageID = anyLanguageId;
    }

    @Override
    public @NotNull String getFamilyName() {
      return DevKitBundle.message("inspection.plugin.xml.extension.registration.should.define.language.tag.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      Extension fixExtension = DomUtil.findDomElement(descriptor.getPsiElement(), Extension.class, false);
      if (fixExtension == null) return;

      XmlTag xmlTag = fixExtension.getXmlTag();
      XmlTag languageTag = xmlTag.createChildTag("language", null, myAnyLanguageID, false);
      XmlTag addedLanguageTag = xmlTag.addSubTag(languageTag, true);
      if (!IntentionPreviewUtils.isPreviewElement(addedLanguageTag)) {
        PsiNavigationSupport.getInstance()
          .createNavigatable(project, addedLanguageTag.getContainingFile().getVirtualFile(),
                             addedLanguageTag.getValue().getTextRange().getEndOffset()).navigate(true);
      }
    }
  }
}
