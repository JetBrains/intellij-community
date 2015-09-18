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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.highlighting.*;
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
    else if (element instanceof AddToGroup) {
      annotateAddToGroup((AddToGroup)element, holder);
    }
  }

  private static void checkJetBrainsPlugin(IdeaPlugin ideaPlugin, DomElementAnnotationHolder holder) {
    final Module module = ideaPlugin.getModule();
    if (module == null) return;
    if (!PsiUtil.isIdeaProject(module.getProject())) return;

    if (ideaPlugin.getPluginId() == null) return;

    final Vendor vendor = ContainerUtil.getFirstItem(ideaPlugin.getVendors());
    if (vendor == null) {
      holder.createProblem(DomUtil.getFileElement(ideaPlugin), "Plugin developed as a part of IntelliJ IDEA project should specify 'JetBrains' as its vendor",
                           new SpecifyJetBrainsAsVendorQuickFix());
    }
    else if (!PluginManagerMain.isDevelopedByJetBrains(vendor.getValue())) {
      holder.createProblem(vendor, "Plugin developed as a part of IntelliJ IDEA project should include 'JetBrains' as one of its vendors");
    }
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

  private static void annotateAddToGroup(AddToGroup addToGroup, DomElementAnnotationHolder holder) {
    if (!DomUtil.hasXml(addToGroup.getRelativeToAction())) return;

    if (!DomUtil.hasXml(addToGroup.getAnchor())) {
      holder.createProblem(addToGroup, "'anchor' must be specified with 'relative-to-action'",
                           new AddDomElementQuickFix<GenericAttributeValue>(addToGroup.getAnchor()));
      return;
    }

    final Anchor value = addToGroup.getAnchor().getValue();
    if (value == Anchor.after || value == Anchor.before) {
      return;
    }
    holder.createProblem(addToGroup.getAnchor(), "Must use '" + Anchor.after + "'|'" + Anchor.before + "' with 'relative-to-action'");
  }

  private static class SpecifyJetBrainsAsVendorQuickFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Specify JetBrains as vendor";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiFile file = descriptor.getPsiElement().getContainingFile();
      DomFileElement<IdeaPlugin> fileElement = DomManager.getDomManager(project).getFileElement((XmlFile)file, IdeaPlugin.class);
      if (fileElement != null) {
        fileElement.getRootElement().addVendor().setValue(PluginManagerMain.JETBRAINS_VENDOR);
      }
    }
  }
}
