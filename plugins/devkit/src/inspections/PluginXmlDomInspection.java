/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import com.intellij.util.xml.highlighting.RemoveDomElementQuickFix;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.List;

/**
 * @author mike
 */
public class PluginXmlDomInspection extends BasicDomElementsInspection<IdeaPlugin> {
  public PluginXmlDomInspection() {
    super(IdeaPlugin.class);
  }

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return DevKitBundle.message("inspections.group.name");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Plugin.xml Validity";
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "PluginXmlValidity";
  }

  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    super.checkDomElement(element, holder, helper);

    if (element instanceof IdeaPlugin) {
      checkJetBrainsPlugin((IdeaPlugin)element, holder);
    }
    else if (element instanceof Extension) {
      annotateExtension((Extension)element, holder);
    }
    else if (element instanceof Vendor) {
      annotateVendor((Vendor)element, holder);
    }
    else if (element instanceof IdeaVersion) {
      annotateIdeaVersion((IdeaVersion)element, holder);
    }
    else if (element instanceof Extensions) {
      annotateExtensions((Extensions)element, holder);
    }
  }

  private static void checkJetBrainsPlugin(IdeaPlugin ideaPlugin, DomElementAnnotationHolder holder) {
    final Module module = ideaPlugin.getModule();
    if (module == null) return;
    if (!PsiUtil.isIdeaProject(module.getProject())) return;

    if (ideaPlugin.getPluginId() == null) return;

    final Vendor vendor = ContainerUtil.getFirstItem(ideaPlugin.getVendors());
    if (vendor == null) return;
    if (!"JetBrains".equals(vendor.getValue())) return;

    for (Extensions extensions : ideaPlugin.getExtensions()) {
      final List<Extension> definedEps = DomUtil.getDefinedChildrenOfType(extensions, Extension.class, true, false);
      for (Extension extension : definedEps) {
        final ExtensionPoint extensionPoint = extension.getExtensionPoint();
        if (extensionPoint == null) continue;
        if ("com.intellij.errorHandler".equals(extensionPoint.getEffectiveQualifiedName())) {
          return;
        }
      }
    }

    holder.createProblem(DomUtil.getFileElement(ideaPlugin),
                         "JetBrains plugin should provide <errorHandler>");
  }

  private static void annotateExtensions(Extensions extensions, DomElementAnnotationHolder holder) {
    final GenericAttributeValue<IdeaPlugin> xmlnsAttribute = extensions.getXmlns();
    if (!DomUtil.hasXml(xmlnsAttribute)) return;

    holder.createProblem(xmlnsAttribute,
                         ProblemHighlightType.LIKE_DEPRECATED,
                         "Use defaultExtensionNs instead", null).highlightWholeElement();
  }

  private static void annotateIdeaVersion(IdeaVersion ideaVersion, DomElementAnnotationHolder holder) {
    highlightNotUsedAnymore(ideaVersion.getMin(), holder);
    highlightNotUsedAnymore(ideaVersion.getMax(), holder);
  }

  private static void annotateExtension(Extension extension, DomElementAnnotationHolder holder) {
    final ExtensionPoint extensionPoint = extension.getExtensionPoint();
    if (extensionPoint == null) return;
    final GenericAttributeValue<PsiClass> interfaceAttribute = extensionPoint.getInterface();
    if (DomUtil.hasXml(interfaceAttribute)) {
      final PsiClass value = interfaceAttribute.getValue();
      if (value != null && value.isDeprecated()) {
        holder.createProblem(extension, ProblemHighlightType.LIKE_DEPRECATED,
                             "Deprecated EP '" + extensionPoint.getEffectiveQualifiedName() + "'", null);
        return;
      }
    }

    final List<? extends DomAttributeChildDescription> descriptions = extension.getGenericInfo().getAttributeChildrenDescriptions();
    for (DomAttributeChildDescription attributeDescription : descriptions) {
      final GenericAttributeValue attributeValue = attributeDescription.getDomAttributeValue(extension);
      if (attributeValue == null || !DomUtil.hasXml(attributeValue)) continue;

      // IconsReferencesContributor
      if ("icon".equals(attributeDescription.getXmlElementName())) {
        final XmlAttributeValue value = attributeValue.getXmlAttributeValue();
        if (value != null) {
          for (PsiReference reference : value.getReferences()) {
            if (reference.resolve() == null) {
              holder.createResolveProblem(attributeValue, reference);
            }
          }
        }
      }

      final PsiElement declaration = attributeDescription.getDeclaration(extension.getManager().getProject());
      if (declaration instanceof PsiField) {
        PsiField psiField = (PsiField)declaration;
        if (psiField.isDeprecated()) {
          holder.createProblem(attributeValue, ProblemHighlightType.LIKE_DEPRECATED,
                               "Deprecated attribute '" + attributeDescription.getName() + "'",
                               null)
            .highlightWholeElement();
        }
      }
    }
  }

  private static void annotateVendor(Vendor vendor, DomElementAnnotationHolder holder) {
    highlightNotUsedAnymore(vendor.getLogo(), holder);
  }

  private static void highlightNotUsedAnymore(GenericAttributeValue attributeValue,
                                              DomElementAnnotationHolder holder) {
    if (!DomUtil.hasXml(attributeValue)) return;

    holder.createProblem(attributeValue,
                         ProblemHighlightType.LIKE_DEPRECATED,
                         "Attribute '" + attributeValue.getXmlElementName() + "' not used anymore",
                         null, new RemoveDomElementQuickFix(attributeValue))
      .highlightWholeElement();
  }
}
