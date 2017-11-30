/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ExtensionPoints;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.diagnostic.ITNReporter;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.highlighting.*;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;
import org.jetbrains.idea.devkit.inspections.quickfix.AddWithTagFix;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author mike
 */
public class PluginXmlDomInspection extends BasicDomElementsInspection<IdeaPlugin> {
  private static final Logger LOG = Logger.getInstance(PluginXmlDomInspection.class);

  public PluginXmlDomInspection() {
    super(IdeaPlugin.class);
  }

  @NotNull
  public String getShortName() {
    return "PluginXmlValidity";
  }

  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    super.checkDomElement(element, holder, helper);

    if (element instanceof IdeaPlugin) {
      annotateIdeaPlugin((IdeaPlugin)element, holder);
      checkJetBrainsPlugin((IdeaPlugin)element, holder);
    }
    else if (element instanceof Extension) {
      annotateExtension((Extension)element, holder);
    }
    else if (element instanceof ExtensionPoint) {
      annotateExtensionPoint((ExtensionPoint)element, holder);
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
    else if (element instanceof Action) {
      annotateAction((Action)element, holder);
    }
    else if (element instanceof Group) {
      annotateGroup((Group)element, holder);
    }
    else if (element instanceof Component) {
      annotateComponent((Component) element, holder);
      if (element instanceof Component.Project) {
        annotateProjectComponent((Component.Project)element, holder);
      }
    }

    if (element instanceof GenericDomValue) {
      final GenericDomValue domValue = (GenericDomValue)element;

      if (domValue.getConverter() instanceof PluginPsiClassConverter) {
        annotatePsiClassValue(domValue, holder);
      }
    }
  }

  private static void annotatePsiClassValue(GenericDomValue domValue, DomElementAnnotationHolder holder) {
    final Object value = domValue.getValue();
    if (value instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)value;
      if (psiClass.getContainingClass() != null &&
          !StringUtil.containsChar(StringUtil.notNullize(domValue.getRawText()), '$')) {
        holder.createProblem(domValue, DevKitBundle.message("inspections.plugin.xml.inner.class.must.be.separated.with.dollar"));
      }
    }
  }

  private static void annotateIdeaPlugin(IdeaPlugin ideaPlugin, DomElementAnnotationHolder holder) {
    //noinspection deprecation
    highlightAttributeNotUsedAnymore(ideaPlugin.getIdeaPluginVersion(), holder);
  }

  private static void checkJetBrainsPlugin(IdeaPlugin ideaPlugin, DomElementAnnotationHolder holder) {
    final Module module = ideaPlugin.getModule();
    if (module == null) return;
    if (!PsiUtil.isIdeaProject(module.getProject())) return;

    String pluginId = ideaPlugin.getPluginId();
    if (pluginId == null || pluginId.equals(PluginManagerCore.CORE_PLUGIN_ID)) return;

    XmlTag xmlTag = ideaPlugin.getXmlTag();
    if (xmlTag == null) return;

    VirtualFile virtualFile = xmlTag.getContainingFile().getVirtualFile();
    if (virtualFile == null ||
        !ModuleRootManager.getInstance(module).getFileIndex().isUnderSourceRootOfType(virtualFile, JavaModuleSourceRootTypes.PRODUCTION)) {
      return;
    }

    final Vendor vendor = ideaPlugin.getVendor();
    if (!DomUtil.hasXml(vendor)) {
      holder.createProblem(DomUtil.getFileElement(ideaPlugin),
                           DevKitBundle.message("inspections.plugin.xml.plugin.should.have.jetbrains.vendor"),
                           new SpecifyJetBrainsAsVendorQuickFix());
    }
    else if (!PluginManagerMain.isDevelopedByJetBrains(vendor.getValue())) {
      holder.createProblem(vendor, DevKitBundle.message("inspections.plugin.xml.plugin.should.include.jetbrains.vendor"));
    }
  }

  private static void annotateExtensionPoint(ExtensionPoint extensionPoint, DomElementAnnotationHolder holder) {
    if (extensionPoint.getWithElements().isEmpty() &&
        !extensionPoint.collectMissingWithTags().isEmpty()) {
      holder.createProblem(extensionPoint, DevKitBundle.message("inspections.plugin.xml.ep.doesnt.have.with"), new AddWithTagFix());
    }

    GenericAttributeValue<String> name = extensionPoint.getName();
    if (!isValidEpName(name)) {
      holder.createProblem(name, ProblemHighlightType.WEAK_WARNING,
                           DevKitBundle.message("inspections.plugin.xml.invalid.ep.name", name.getValue()), null);
    }
    GenericAttributeValue<String> qualifiedName = extensionPoint.getQualifiedName();
    if (!isValidEpName(qualifiedName)) {
      holder.createProblem(qualifiedName, ProblemHighlightType.WEAK_WARNING,
                           DevKitBundle.message("inspections.plugin.xml.invalid.ep.qualified.name", qualifiedName.getValue()), null);
    }

    Module module = extensionPoint.getModule();
    if (ComponentModuleRegistrationChecker.isIdeaPlatformModule(module)) {
      ComponentModuleRegistrationChecker.checkProperModule(extensionPoint, holder);
    }

  }

  private static boolean isValidEpName(GenericAttributeValue<String> nameAttrValue) {
    if (!nameAttrValue.exists()) {
      return true;
    }
    @NonNls String name = nameAttrValue.getValue();
    return StringUtil.isNotEmpty(name) &&
           Character.isLowerCase(name.charAt(0)) && // also checks that name doesn't start with dot
           !name.toUpperCase().equals(name) && // not all uppercase
           StringUtil.isLatinAlphanumeric(name.replace(".", "")) &&
           name.charAt(name.length() - 1) != '.';
  }

  private static void annotateExtensions(Extensions extensions, DomElementAnnotationHolder holder) {
    //noinspection deprecation
    final GenericAttributeValue<IdeaPlugin> xmlnsAttribute = extensions.getXmlns();
    if (DomUtil.hasXml(xmlnsAttribute)) {
      highlightDeprecated(xmlnsAttribute, DevKitBundle.message("inspections.plugin.xml.use.defaultExtensionNs"), holder, false, true);
      return;
    }

    if (!DomUtil.hasXml(extensions.getDefaultExtensionNs())) {
      holder.createProblem(
        extensions,
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        DevKitBundle.message("inspections.plugin.xml.specify.defaultExtensionNs.explicitly", Extensions.DEFAULT_PREFIX),
        null,
        new AddDomElementQuickFix<GenericAttributeValue>(extensions.getDefaultExtensionNs()) {
          @Override
          public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            super.applyFix(project, descriptor);
            myElement.setStringValue(Extensions.DEFAULT_PREFIX);
          }
        });
    }
  }

  private static void annotateIdeaVersion(IdeaVersion ideaVersion, DomElementAnnotationHolder holder) {
    //noinspection deprecation
    highlightAttributeNotUsedAnymore(ideaVersion.getMin(), holder);
    //noinspection deprecation
    highlightAttributeNotUsedAnymore(ideaVersion.getMax(), holder);
    highlightUntilBuild(ideaVersion, holder);
  }

  private static void highlightUntilBuild(IdeaVersion ideaVersion, DomElementAnnotationHolder holder) {
    String untilBuild = ideaVersion.getUntilBuild().getStringValue();
    if (untilBuild != null && isStarSupported(ideaVersion.getSinceBuild().getStringValue())) {
      Matcher matcher = IdeaPluginDescriptorImpl.EXPLICIT_BIG_NUMBER_PATTERN.matcher(untilBuild);
      if (matcher.matches()) {
        holder.createProblem(
          ideaVersion.getUntilBuild(),
          DevKitBundle.message("inspections.plugin.xml.until.build.use.asterisk.instead.of.big.number", matcher.group(2)),
          new CorrectUntilBuildAttributeFix(
            IdeaPluginDescriptorImpl.convertExplicitBigNumberInUntilBuildToStar(untilBuild)));
      }
      if (untilBuild.matches("\\d+")) {
        int branch = Integer.parseInt(untilBuild);
        String corrected = (branch - 1) + ".*";
        holder.createProblem(ideaVersion.getUntilBuild(),
                             DevKitBundle.message("inspections.plugin.xml.until.build.misleading.plain.number", untilBuild, corrected),
                             new CorrectUntilBuildAttributeFix(corrected));
      }
    }
  }

  private static final Pattern BASE_LINE_EXTRACTOR = Pattern.compile("(?:\\p{javaLetter}+-)?(\\d+)(?:\\..*)?");
  private static final int FIRST_BRANCH_SUPPORTING_STAR = 131;

  private static boolean isStarSupported(String buildNumber) {
    if (buildNumber == null) return false;
    Matcher matcher = BASE_LINE_EXTRACTOR.matcher(buildNumber);
    if (matcher.matches()) {
      int branch = Integer.parseInt(matcher.group(1));
      return branch >= FIRST_BRANCH_SUPPORTING_STAR;
    }
    return false;
  }

  private static void annotateExtension(Extension extension, DomElementAnnotationHolder holder) {
    final ExtensionPoint extensionPoint = extension.getExtensionPoint();
    if (extensionPoint == null) return;
    final GenericAttributeValue<PsiClass> interfaceAttribute = extensionPoint.getInterface();
    if (DomUtil.hasXml(interfaceAttribute)) {
      final PsiClass value = interfaceAttribute.getValue();
      if (value != null && value.isDeprecated()) {
        highlightDeprecated(
          extension, DevKitBundle.message("inspections.plugin.xml.deprecated.ep", extensionPoint.getEffectiveQualifiedName()),
          holder, false, false);
        return;
      }
    }

    if (ExtensionPoints.ERROR_HANDLER.equals(extensionPoint.getEffectiveQualifiedName()) && extension.exists()) {
      String implementation = extension.getXmlTag().getAttributeValue("implementation");
      if (ITNReporter.class.getName().equals(implementation)) {
        IdeaPlugin plugin = extension.getParentOfType(IdeaPlugin.class, true);
        if (plugin != null) {
          Vendor vendor = plugin.getVendor();
          if (DomUtil.hasXml(vendor) && PluginManagerMain.isDevelopedByJetBrains(vendor.getValue())) {
            LocalQuickFix fix = new RemoveDomElementQuickFix(extension);
            holder.createProblem(extension, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                 DevKitBundle.message("inspections.plugin.xml.no.need.to.specify.itnReporter"),
                                 null, fix).highlightWholeElement();
          }
        }
      }
    }

    final List<? extends DomAttributeChildDescription> descriptions = extension.getGenericInfo().getAttributeChildrenDescriptions();
    for (DomAttributeChildDescription attributeDescription : descriptions) {
      final GenericAttributeValue attributeValue = attributeDescription.getDomAttributeValue(extension);
      if (attributeValue == null || !DomUtil.hasXml(attributeValue)) continue;

      // IconsReferencesContributor
      if ("icon".equals(attributeDescription.getXmlElementName())) {
        annotateResolveProblems(holder, attributeValue);
      }
      else if ("order".equals(attributeDescription.getXmlElementName())) {
        annotateOrderAttributeProblems(holder, attributeValue);
      }

      final PsiElement declaration = attributeDescription.getDeclaration(extension.getManager().getProject());
      if (declaration instanceof PsiField) {
        PsiField psiField = (PsiField)declaration;
        if (psiField.isDeprecated()) {
          highlightDeprecated(
            attributeValue, DevKitBundle.message("inspections.plugin.xml.deprecated.attribute", attributeDescription.getName()),
            holder, false, true);
        }
      }
    }

    Module module = extension.getModule();
    if (ComponentModuleRegistrationChecker.isIdeaPlatformModule(module)) {
      ComponentModuleRegistrationChecker.checkProperXmlFileForExtension(extension, holder);
    }
  }

  private static void annotateComponent(Component component, DomElementAnnotationHolder holder) {
    Module module = component.getModule();
    if (ComponentModuleRegistrationChecker.isIdeaPlatformModule(module)) {
      ComponentModuleRegistrationChecker.checkProperXmlFileForClass(component, holder, component.getImplementationClass().getValue());
    }
  }


  private static void annotateVendor(Vendor vendor, DomElementAnnotationHolder holder) {
    //noinspection deprecation
    highlightAttributeNotUsedAnymore(vendor.getLogo(), holder);
  }

  private static void highlightAttributeNotUsedAnymore(GenericAttributeValue attributeValue,
                                                       DomElementAnnotationHolder holder) {
    if (!DomUtil.hasXml(attributeValue)) return;
    highlightDeprecated(
      attributeValue, DevKitBundle.message("inspections.plugin.xml.attribute.not.used.anymore", attributeValue.getXmlElementName()),
      holder, true, true);
  }

  private static void annotateAddToGroup(AddToGroup addToGroup, DomElementAnnotationHolder holder) {
    if (!DomUtil.hasXml(addToGroup.getRelativeToAction())) return;

    if (!DomUtil.hasXml(addToGroup.getAnchor())) {
      holder.createProblem(addToGroup, DevKitBundle.message("inspections.plugin.xml.anchor.must.have.relative-to-action"),
                           new AddDomElementQuickFix<GenericAttributeValue>(addToGroup.getAnchor()));
      return;
    }

    final Anchor value = addToGroup.getAnchor().getValue();
    if (value == Anchor.after || value == Anchor.before) {
      return;
    }
    holder.createProblem(
      addToGroup.getAnchor(),
      DevKitBundle.message("inspections.plugin.xml.must.use.after.before.with.relative-to-action", Anchor.after, Anchor.before));
  }

  private static void annotateGroup(Group group, DomElementAnnotationHolder holder) {
    final GenericAttributeValue<String> iconAttribute = group.getIcon();
    if (DomUtil.hasXml(iconAttribute)) {
      annotateResolveProblems(holder, iconAttribute);
    }
  }

  private static void annotateAction(Action action, DomElementAnnotationHolder holder) {
    final GenericAttributeValue<String> iconAttribute = action.getIcon();
    if (DomUtil.hasXml(iconAttribute)) {
      annotateResolveProblems(holder, iconAttribute);
    }
  }

  private static void annotateProjectComponent(Component.Project projectComponent, DomElementAnnotationHolder holder) {
    //noinspection deprecation
    GenericDomValue<Boolean> skipForDefault = projectComponent.getSkipForDefaultProject();
    if (skipForDefault.exists()) {
      highlightDeprecated(skipForDefault, DevKitBundle.message("inspections.plugin.xml.skipForDefaultProject.deprecated"),
                          holder, true, true);
    }
  }

  private static void annotateOrderAttributeProblems(DomElementAnnotationHolder holder, GenericAttributeValue attributeValue) {
    String orderValue = attributeValue.getStringValue();
    if (StringUtil.isEmpty(orderValue)) {
      return;
    }

    try {
      LoadingOrder.readOrder(orderValue);
    } catch (AssertionError ignore) {
      holder.createProblem(attributeValue, HighlightSeverity.ERROR, DevKitBundle.message("inspections.plugin.xml.invalid.order.attribute"));
      return; // no need to resolve references for invalid attribute value
    }

    annotateResolveProblems(holder, attributeValue);
  }

  private static void annotateResolveProblems(DomElementAnnotationHolder holder, GenericAttributeValue attributeValue) {
    final XmlAttributeValue value = attributeValue.getXmlAttributeValue();
    if (value != null) {
      for (PsiReference reference : value.getReferences()) {
        if (reference.resolve() == null) {
          holder.createResolveProblem(attributeValue, reference);
        }
      }
    }
  }

  private static void highlightDeprecated(DomElement element, String message, DomElementAnnotationHolder holder,
                                          boolean useRemoveQuickfix, boolean highlightWholeElement) {
    DomElementProblemDescriptor problem;
    if (!useRemoveQuickfix) {
      problem = holder.createProblem(element, ProblemHighlightType.LIKE_DEPRECATED, message, null);
    }
    else {
      problem = holder.createProblem(element, ProblemHighlightType.LIKE_DEPRECATED, message, null, new RemoveDomElementQuickFix(element));
    }
    if (highlightWholeElement) {
      problem.highlightWholeElement();
    }
  }

  private static class CorrectUntilBuildAttributeFix implements LocalQuickFix {
    private final String myCorrectValue;

    public CorrectUntilBuildAttributeFix(String correctValue) {
      myCorrectValue = correctValue;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Change 'until-build' to '" + myCorrectValue + "'";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Correct 'until-build' attribute";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final XmlAttribute attribute = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), XmlAttribute.class, false);
      //noinspection unchecked
      final GenericAttributeValue<String> domElement = DomManager.getDomManager(project).getDomElement(attribute);
      LOG.assertTrue(domElement != null);
      domElement.setStringValue(myCorrectValue);
    }
  }

  private static class SpecifyJetBrainsAsVendorQuickFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Specify JetBrains as vendor";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiFile file = descriptor.getPsiElement().getContainingFile();
      DomFileElement<IdeaPlugin> fileElement = DomManager.getDomManager(project).getFileElement((XmlFile)file, IdeaPlugin.class);
      if (fileElement != null) {
        IdeaPlugin root = fileElement.getRootElement();
        XmlTag after = getLastSubTag(root, root.getId(), root.getDescription(), root.getVersion(), root.getName());
        XmlTag rootTag = root.getXmlTag();
        XmlTag vendorTag = rootTag.createChildTag("vendor", rootTag.getNamespace(), PluginManagerMain.JETBRAINS_VENDOR, false);
        if (after == null) {
          rootTag.addSubTag(vendorTag, true);
        }
        else {
          rootTag.addAfter(vendorTag, after);
        }
      }
    }

    private static XmlTag getLastSubTag(IdeaPlugin root, DomElement... children) {
      Set<XmlTag> childrenTags = new HashSet<>();
      for (DomElement child : children) {
        if (child != null) {
          childrenTags.add(child.getXmlTag());
        }
      }
      XmlTag[] subTags = root.getXmlTag().getSubTags();
      for (int i = subTags.length - 1; i >= 0; i--) {
        if (childrenTags.contains(subTags[i])) {
          return subTags[i];
        }
      }
      return null;
    }
  }
}
