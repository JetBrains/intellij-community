// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.MoveToPackageFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.diagnostic.ITNReporter;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.highlighting.*;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.xml.CommonXmlStrings;
import com.intellij.xml.util.IncludedXmlTag;
import org.jetbrains.annotations.*;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.dom.Listeners.Listener;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;
import org.jetbrains.idea.devkit.dom.index.ExtensionPointIndex;
import org.jetbrains.idea.devkit.inspections.quickfix.AddWithTagFix;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PluginPlatformInfo;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.psi.search.GlobalSearchScope.projectScope;

@VisibleForTesting
@ApiStatus.Internal
public final class PluginXmlDomInspection extends DevKitPluginXmlInspectionBase {

  private static final int MINIMAL_DESCRIPTION_LENGTH = 40;

  @NonNls
  public static final String DEPENDENCIES_DOC_URL =
    "https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html?from=DevkitPluginXmlInspection";

  private static class Holder {
    private static final Pattern BASE_LINE_EXTRACTOR = Pattern.compile("(?:\\p{javaLetter}+-)?(\\d+)(?:\\..*)?");
  }

  private static final int FIRST_BRANCH_SUPPORTING_STAR = 131;

  public boolean myIgnoreUnstableApiDeclaredInThisProject = false;

  @Override
  @NotNull
  public String getShortName() {
    return "PluginXmlValidity";
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.checkbox("myIgnoreUnstableApiDeclaredInThisProject",
                       DevKitBundle.message("devkit.unstable.api.usage.ignore.declared.inside.this.project"))
    );
  }

  @Override
  protected void checkDomElement(@NotNull DomElement element,
                                 @NotNull DomElementAnnotationHolder holder,
                                 @NotNull DomHighlightingHelper helper) {
    if (!isAllowed(holder)) return;

    super.checkDomElement(element, holder, helper);

    if (element instanceof IdeaPlugin) {
      Module module = element.getModule();
      if (module != null) {
        annotateIdeaPlugin((IdeaPlugin)element, holder, module);
        checkJetBrainsPlugin((IdeaPlugin)element, holder, module);
      }
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
    else if (element instanceof ProductDescriptor) {
      annotateProductDescriptor((ProductDescriptor)element, holder);
    }
    else if (element instanceof IdeaVersion) {
      annotateIdeaVersion((IdeaVersion)element, holder);
    }
    else if (element instanceof Dependency) {
      annotateDependency((Dependency)element, holder);
    }
    else if (element instanceof DependencyDescriptor) {
      annotateDependencyDescriptor((DependencyDescriptor)element, holder);
    }
    else if (element instanceof ContentDescriptor) {
      annotateContentDescriptor((ContentDescriptor)element, holder);
    }
    else if (element instanceof Extensions) {
      annotateExtensions((Extensions)element, holder);
    }
    else if (element instanceof Extensions.UnresolvedExtension) {
      annotateUnresolvedExtension((Extensions.UnresolvedExtension)element, holder);
    }
    else if (element instanceof AddToGroup) {
      annotateAddToGroup((AddToGroup)element, holder);
    }
    else if (element instanceof Action) {
      annotateAction((Action)element, holder);
    }
    else if (element instanceof Synonym) {
      annotateSynonym((Synonym)element, holder);
    }
    else if (element instanceof Group) {
      annotateGroup((Group)element, holder);
    }
    else if (element instanceof Component) {
      annotateComponent((Component)element, holder);
      if (element instanceof Component.Project) {
        annotateProjectComponent((Component.Project)element, holder);
      }
    }
    else //noinspection deprecation
      if (element instanceof Helpset) {
        highlightRedundant(element, DevKitBundle.message("inspections.plugin.xml.deprecated.helpset"), holder);
      }
      else if (element instanceof Listeners) {
        annotateListeners((Listeners)element, holder);
      }
      else if (element instanceof Listener) {
        annotateListener((Listener)element, holder);
      }

    if (element instanceof GenericDomValue<?> domValue) {
      if (domValue.getConverter() instanceof PluginPsiClassConverter) {
        @SuppressWarnings("unchecked") GenericDomValue<PsiClass> psiClassDomValue = (GenericDomValue<PsiClass>)element;
        annotatePsiClassValue(psiClassDomValue, holder);
      }
    }
  }

  private static void annotateDependencyDescriptor(DependencyDescriptor descriptor, DomElementAnnotationHolder holder) {
    if (descriptor.getModuleEntry().isEmpty() &&
        descriptor.getPlugin().isEmpty()) {
      holder.createProblem(descriptor, HighlightSeverity.ERROR,
                           DevKitBundle.message("inspections.plugin.xml.dependency.descriptor.at.least.one.dependency"));
      return;
    }

    final IdeaPlugin ideaPlugin = descriptor.getParentOfType(IdeaPlugin.class, false);
    assert ideaPlugin != null;
    for (Dependency dependency : ideaPlugin.getDepends()) {
      if (dependency.getOptional().getValue() == Boolean.TRUE) continue;
      holder.createProblem(dependency, HighlightSeverity.ERROR,
                           DevKitBundle.message("inspections.plugin.xml.dependency.descriptor.cannot.use.depends")).highlightWholeElement();
    }
  }

  private static void annotateContentDescriptor(ContentDescriptor descriptor, DomElementAnnotationHolder holder) {
    if (descriptor.getModuleEntry().isEmpty()) {
      holder.createProblem(descriptor, HighlightSeverity.ERROR,
                           DevKitBundle.message("inspections.plugin.xml.module.descriptor.at.least.one.dependency"));
    }
  }

  private static void annotatePsiClassValue(GenericDomValue<PsiClass> psiClassDomValue, DomElementAnnotationHolder holder) {
    final PsiClass psiClass = psiClassDomValue.getValue();
    if (psiClass == null) return;
    if (psiClass.getContainingClass() != null &&
        !StringUtil.containsChar(StringUtil.notNullize(psiClassDomValue.getRawText()), '$')) {
      holder.createProblem(psiClassDomValue, DevKitBundle.message("inspections.plugin.xml.inner.class.must.be.separated.with.dollar"));
    }

    Module module = psiClassDomValue.getModule();
    if (module == null) return;

    IdeaPlugin ideaPlugin = psiClassDomValue.getParentOfType(IdeaPlugin.class, true);
    assert ideaPlugin != null;
    String pluginPackage = ideaPlugin.getPackage().getStringValue();
    if (pluginPackage == null) return;

    final String psiClassFqn = psiClass.getQualifiedName();
    assert psiClassFqn != null;

    // only highlight if located in the same module
    if (!StringUtil.startsWith(psiClassFqn, pluginPackage + ".") &&
        module == ModuleUtilCore.findModuleForPsiElement(psiClass)) {
      holder.createProblem(psiClassDomValue, HighlightSeverity.ERROR,
                           DevKitBundle.message("inspections.plugin.xml.dependency.class.located.in.wrong.package",
                                                psiClassFqn, pluginPackage),
                           new MoveToPackageFix(psiClass.getContainingFile(), pluginPackage));
    }
  }

  private static void annotateListener(Listener listener, DomElementAnnotationHolder holder) {
    final PsiClass listenerClass = listener.getListenerClassName().getValue();
    final PsiClass topicClass = listener.getTopicClassName().getValue();
    if (listenerClass == null || topicClass == null) return;

    if (!listenerClass.isInheritor(topicClass, true)) {
      holder.createProblem(listener.getListenerClassName(),
                           DevKitBundle.message("inspections.plugin.xml.listener.does.not.inherit",
                                                listener.getListenerClassName().getStringValue(),
                                                listener.getTopicClassName().getStringValue()));
    }
  }

  private static final int LISTENERS_PLATFORM_VERSION = 193;
  private static final int LISTENERS_OS_ATTRIBUTE_PLATFORM_VERSION = 201;

  private static void annotateListeners(Listeners listeners, DomElementAnnotationHolder holder) {
    final Module module = listeners.getModule();
    if (module == null || IntelliJProjectUtil.isIntelliJPlatformProject(module.getProject())) return;

    PluginPlatformInfo platformInfo = PluginPlatformInfo.forDomElement(listeners);
    final PluginPlatformInfo.PlatformResolveStatus resolveStatus = platformInfo.getResolveStatus();

    if (resolveStatus == PluginPlatformInfo.PlatformResolveStatus.DEVKIT_NO_MAIN) {
      holder.createProblem(listeners, ProblemHighlightType.ERROR,
                           DevKitBundle.message("inspections.plugin.xml.since.build.could.not.locate.main.descriptor"), null)
        .highlightWholeElement();
      return;
    }

    if (resolveStatus == PluginPlatformInfo.PlatformResolveStatus.DEVKIT_NO_SINCE_BUILD) {
      final boolean noSinceBuildXml = !DomUtil.hasXml(platformInfo.getMainIdeaPlugin().getIdeaVersion().getSinceBuild());
      LocalQuickFix[] fixes =
        noSinceBuildXml ? new LocalQuickFix[]{new AddDomElementQuickFix<>(platformInfo.getMainIdeaPlugin().getIdeaVersion())} :
        LocalQuickFix.EMPTY_ARRAY;
      holder.createProblem(listeners, ProblemHighlightType.ERROR,
                           DevKitBundle.message("inspections.plugin.xml.since.build.must.be.specified"), null,
                           fixes)
        .highlightWholeElement();
      return;
    }

    final BuildNumber buildNumber = platformInfo.getSinceBuildNumber();
    if (buildNumber == null) {
      holder.createProblem(listeners, ProblemHighlightType.ERROR,
                           DevKitBundle.message("inspections.plugin.xml.since.build.could.not.determine.platform.version"), null)
        .highlightWholeElement();
      return;
    }

    final int baselineVersion = buildNumber.getBaselineVersion();

    boolean canHaveOsAttribute = baselineVersion >= LISTENERS_OS_ATTRIBUTE_PLATFORM_VERSION;
    if (!canHaveOsAttribute) {
      for (Listener listener : listeners.getListeners()) {
        if (DomUtil.hasXml(listener.getOs())) {
          holder.createProblem(listener.getOs(), ProblemHighlightType.ERROR,
                               DevKitBundle.message("inspections.plugin.xml.since.build.listeners.os.attribute",
                                                    LISTENERS_OS_ATTRIBUTE_PLATFORM_VERSION,
                                                    baselineVersion),
                               null)
            .highlightWholeElement();
        }
      }
    }


    if (baselineVersion >= LISTENERS_PLATFORM_VERSION) return;

    holder.createProblem(listeners, ProblemHighlightType.ERROR,
                         DevKitBundle.message("inspections.plugin.xml.since.build.listeners.not.available",
                                              LISTENERS_PLATFORM_VERSION,
                                              baselineVersion),
                         null)
      .highlightWholeElement();
  }

  private static void annotateDependency(Dependency dependency, DomElementAnnotationHolder holder) {
    final GenericAttributeValue<Boolean> optional = dependency.getOptional();
    if (optional.getValue() == Boolean.FALSE) {
      highlightRedundant(optional,
                         DevKitBundle.message("inspections.plugin.xml.dependency.superfluous.optional"),
                         ProblemHighlightType.WARNING, holder);
    }
    else if (optional.getValue() == Boolean.TRUE &&
             !DomUtil.hasXml(dependency.getConfigFile())) {
      holder.createProblem(dependency, DevKitBundle.message("inspections.plugin.xml.dependency.specify.config.file"),
                           new AddDomElementQuickFix<>(dependency.getConfigFile())).highlightWholeElement();
    }
  }

  private static void annotateIdeaPlugin(IdeaPlugin ideaPlugin, DomElementAnnotationHolder holder, @NotNull Module module) {
    //noinspection deprecation
    highlightAttributeNotUsedAnymore(ideaPlugin.getIdeaPluginVersion(), holder);
    //noinspection deprecation
    if (DomUtil.hasXml(ideaPlugin.getUseIdeaClassloader())) {
      //noinspection deprecation
      highlightDeprecated(ideaPlugin.getUseIdeaClassloader(), DevKitBundle.message("inspections.plugin.xml.deprecated"), holder, true,
                          true);
    }

    checkMaxLength(ideaPlugin.getUrl(), 255, holder);
    checkValidWebsite(ideaPlugin.getUrl(), holder);

    checkMaxLength(ideaPlugin.getId(), 255, holder);

    checkTemplateText(ideaPlugin.getName(), "Plugin display name here", holder);
    checkTemplateTextContainsWord(ideaPlugin.getName(), holder, "plugin", "IntelliJ", "JetBrains");
    checkMaxLength(ideaPlugin.getName(), 255, holder);


    checkMaxLength(ideaPlugin.getDescription(), 65535, holder);
    checkHasRealText(ideaPlugin.getDescription(), holder);
    checkTemplateTextContains(ideaPlugin.getDescription(), "Enter short description for your plugin here.", holder);
    checkTemplateTextContains(ideaPlugin.getDescription(), "most HTML tags may be used", holder);

    checkMaxLength(ideaPlugin.getChangeNotes(), 65535, holder);
    checkTemplateTextContains(ideaPlugin.getChangeNotes(), "Add change notes here", holder);
    checkTemplateTextContains(ideaPlugin.getChangeNotes(), "most HTML tags may be used", holder);

    if (!ideaPlugin.hasRealPluginId()) return;

    MultiMap<String, Dependency> dependencies = MultiMap.create();
    ideaPlugin.getDepends().forEach(dependency -> {
      if (DomUtil.hasXml(dependency.getConfigFile())) {
        dependencies.putValue(dependency.getConfigFile().getStringValue(), dependency);
      }
    });
    for (Map.Entry<String, Collection<Dependency>> entry : dependencies.entrySet()) {
      if (entry.getValue().size() > 1) {
        for (Dependency dependency : entry.getValue()) {
          if (dependency.getXmlTag() instanceof IncludedXmlTag) continue;
          highlightRedundant(dependency, DevKitBundle.message("inspections.plugin.xml.duplicated.dependency", entry.getKey()),
                             ProblemHighlightType.ERROR, holder);
        }
      }
    }


    boolean isNotIdeaProject = !IntelliJProjectUtil.isIntelliJPlatformProject(module.getProject());

    if (isNotIdeaProject &&
        !DomUtil.hasXml(ideaPlugin.getVersion()) &&
        PluginModuleType.isOfType(module)) {
      holder.createProblem(ideaPlugin, DevKitBundle.message("inspections.plugin.xml.version.must.be.specified"),
                           new AddMissingMainTag(DevKitBundle.message("inspections.plugin.xml.add.version.tag"), ideaPlugin.getVersion(),
                                                 ""));
    }
    checkMaxLength(ideaPlugin.getVersion(), 64, holder);


    if (isNotIdeaProject && !DomUtil.hasXml(ideaPlugin.getVendor())) {
      holder.createProblem(ideaPlugin, DevKitBundle.message("inspections.plugin.xml.vendor.must.be.specified"),
                           new AddMissingMainTag(DevKitBundle.message("inspections.plugin.xml.add.vendor.tag"), ideaPlugin.getVendor(),
                                                 ""));
    }
  }

  private static void checkJetBrainsPlugin(IdeaPlugin ideaPlugin, DomElementAnnotationHolder holder, @NotNull Module module) {
    if (!IntelliJProjectUtil.isIntelliJPlatformProject(module.getProject())) return;

    if (DomUtil.hasXml(ideaPlugin.getUrl())) {
      String url = ideaPlugin.getUrl().getStringValue();
      if ("https://www.jetbrains.com/idea".equals(url)) {
        highlightRedundant(ideaPlugin.getUrl(),
                           DevKitBundle.message("inspections.plugin.xml.plugin.jetbrains.no.generic.plugin.url"), holder);
      }
    }

    if (!ideaPlugin.hasRealPluginId()) return;

    @NonNls String id = ideaPlugin.getId().getStringValue();
    if (id != null &&
        (StringUtil.startsWith(id, "com.android.") || //NON-NLS
         id.equals("org.jetbrains.android"))) {
      return;
    }

    if (!isUnderProductionSources(ideaPlugin, module)) return;

    final Vendor vendor = ideaPlugin.getVendor();
    if (!DomUtil.hasXml(vendor)) {
      holder.createProblem(DomUtil.getFileElement(ideaPlugin),
                           DevKitBundle.message("inspections.plugin.xml.plugin.should.have.jetbrains.vendor"),
                           new AddMissingMainTag(DevKitBundle.message("inspections.plugin.xml.vendor.specify.jetbrains"),
                                                 vendor, PluginManagerCore.VENDOR_JETBRAINS));
    }
    else if (!PluginManagerCore.isVendorJetBrains(vendor.getValue())) {
      holder.createProblem(vendor, DevKitBundle.message("inspections.plugin.xml.plugin.should.have.jetbrains.vendor"));
    }
    else {
      final String url = vendor.getUrl().getStringValue();
      if (url != null && StringUtil.endsWith(url, "jetbrains.com")) {
        highlightRedundant(vendor.getUrl(),
                           DevKitBundle.message("inspections.plugin.xml.plugin.jetbrains.vendor.no.url", url), holder);
      }
    }

    if (DomUtil.hasXml(vendor.getEmail())) {
      highlightRedundant(vendor.getEmail(),
                         DevKitBundle.message("inspections.plugin.xml.plugin.jetbrains.vendor.no.email"), holder);
    }
    if (DomUtil.hasXml(ideaPlugin.getChangeNotes())) {
      highlightRedundant(ideaPlugin.getChangeNotes(),
                         DevKitBundle.message("inspections.plugin.xml.plugin.jetbrains.no.change.notes"), holder);
    }
    if (DomUtil.hasXml(ideaPlugin.getVersion())) {
      highlightRedundant(ideaPlugin.getVersion(),
                         DevKitBundle.message("inspections.plugin.xml.plugin.jetbrains.no.version"), holder);
    }
    if (DomUtil.hasXml(ideaPlugin.getIdeaVersion())) {
      highlightRedundant(ideaPlugin.getIdeaVersion(),
                         DevKitBundle.message("inspections.plugin.xml.plugin.jetbrains.no.idea.version"), holder);
    }
  }

  private static void annotateExtensionPoint(ExtensionPoint extensionPoint,
                                             DomElementAnnotationHolder holder) {
    if (extensionPoint.getWithElements().isEmpty() &&
        !extensionPoint.collectMissingWithTags().isEmpty()) {
      holder.createProblem(extensionPoint, DevKitBundle.message("inspections.plugin.xml.ep.doesnt.have.with"), new AddWithTagFix());
    }

    checkEpBeanClassAndInterface(extensionPoint, holder);
    checkEpNameAndQualifiedName(extensionPoint, holder);

    if (DomUtil.hasXml(extensionPoint.getQualifiedName())) {
      IdeaPlugin ideaPlugin = DomUtil.getParentOfType(extensionPoint, IdeaPlugin.class, true);
      assert ideaPlugin != null;
      final String pluginId = ideaPlugin.getPluginId();
      if (pluginId != null) {
        final String epQualifiedName = extensionPoint.getQualifiedName().getStringValue();
        if (epQualifiedName != null && epQualifiedName.startsWith(pluginId + ".")) {
          holder.createProblem(extensionPoint.getQualifiedName(), ProblemHighlightType.WARNING,
                               DevKitBundle.message("inspections.plugin.xml.ep.qualifiedName.superfluous"), null,
                               new LocalQuickFix() {
                                 @Override
                                 public @IntentionFamilyName @NotNull String getFamilyName() {
                                   return DevKitBundle.message("inspections.plugin.xml.ep.qualifiedName.superfluous.fix");
                                 }

                                 @Override
                                 public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
                                   ExtensionPoint fixExtensionPoint =
                                     DomUtil.findDomElement(descriptor.getPsiElement(), ExtensionPoint.class);
                                   if (fixExtensionPoint == null) return;

                                   fixExtensionPoint.getQualifiedName().undefine();
                                   fixExtensionPoint.getName().setStringValue(StringUtil.substringAfter(epQualifiedName, pluginId + "."));
                                 }
                               }).highlightWholeElement();
        }
      }
    }
  }

  private static void checkEpBeanClassAndInterface(ExtensionPoint extensionPoint, DomElementAnnotationHolder holder) {
    boolean hasBeanClass = DomUtil.hasXml(extensionPoint.getBeanClass());
    boolean hasInterface = DomUtil.hasXml(extensionPoint.getInterface());
    if (hasBeanClass && hasInterface) {
      highlightRedundant(extensionPoint.getBeanClass(),
                         DevKitBundle.message("inspections.plugin.xml.ep.both.beanClass.and.interface"),
                         ProblemHighlightType.GENERIC_ERROR, holder);
      highlightRedundant(extensionPoint.getInterface(),
                         DevKitBundle.message("inspections.plugin.xml.ep.both.beanClass.and.interface"),
                         ProblemHighlightType.GENERIC_ERROR, holder);
    }
    else if (!hasBeanClass && !hasInterface) {
      holder.createProblem(extensionPoint, ProblemHighlightType.GENERIC_ERROR,
                           DevKitBundle.message("inspections.plugin.xml.ep.missing.beanClass.and.interface"), null,
                           new AddDomElementQuickFix<>(extensionPoint.getBeanClass()),
                           new AddDomElementQuickFix<>(extensionPoint.getInterface()));
    }
  }


  private static void checkEpNameAndQualifiedName(ExtensionPoint extensionPoint, DomElementAnnotationHolder holder) {
    GenericAttributeValue<String> name = extensionPoint.getName();
    GenericAttributeValue<String> qualifiedName = extensionPoint.getQualifiedName();
    boolean hasName = DomUtil.hasXml(name);
    boolean hasQualifiedName = DomUtil.hasXml(qualifiedName);

    if (hasName && hasQualifiedName) {
      holder.createProblem(extensionPoint, ProblemHighlightType.GENERIC_ERROR,
                           DevKitBundle.message("inspections.plugin.xml.ep.both.name.and.qualifiedName"), null);
    }
    else if (!hasName && !hasQualifiedName) {
      holder.createProblem(extensionPoint, ProblemHighlightType.GENERIC_ERROR,
                           DevKitBundle.message("inspections.plugin.xml.ep.missing.name.and.qualifiedName"), null);
    }

    if (hasQualifiedName) {
      if (!isValidEpName(qualifiedName)) {
        String message = DevKitBundle.message("inspections.plugin.xml.invalid.ep.name.description",
                                              DevKitBundle.message("inspections.plugin.xml.invalid.ep.qualifiedName"),
                                              qualifiedName.getValue());
        holder.createProblem(qualifiedName, ProblemHighlightType.WEAK_WARNING, message, null);
      }
      return;
    }

    if (hasName && !isValidEpName(name)) {
      String message = DevKitBundle.message("inspections.plugin.xml.invalid.ep.name.description",
                                            DevKitBundle.message("inspections.plugin.xml.invalid.ep.name"),
                                            name.getValue());
      holder.createProblem(name, ProblemHighlightType.WEAK_WARNING, message, null);
    }
  }

  private static boolean isValidEpName(GenericAttributeValue<String> nameAttrValue) {
    if (!nameAttrValue.exists()) {
      return true;
    }
    @NonNls String name = nameAttrValue.getValue();

    // skip some known offenders in IJ project
    if (name != null
        && (StringUtil.startsWith(name, "Pythonid.") ||
            StringUtil.startsWith(name, "DevKit."))) {
      if (IntelliJProjectUtil.isIntelliJPlatformProject(nameAttrValue.getManager().getProject())) {
        return true;
      }
    }

    if (StringUtil.isEmpty(name) ||
        !Character.isLowerCase(name.charAt(0)) || // also checks that the name doesn't start with a dot
        StringUtil.toUpperCase(name).equals(name) || // not all uppercase chars
        !StringUtil.isLatinAlphanumeric(name.replace(".", "")) ||
        name.charAt(name.length() - 1) == '.') {
      return false;
    }

    List<String> fragments = StringUtil.split(name, ".");
    if (ContainerUtil.exists(fragments, f -> Character.isUpperCase(f.charAt(0)))) {
      return false;
    }

    String epName = fragments.get(fragments.size() - 1);
    List<String> butlast = fragments.subList(0, fragments.size() - 1);
    List<String> words = StringUtil.getWordsIn(epName);
    return !ContainerUtil.exists(words, w -> ContainerUtil.exists(butlast, f -> StringUtil.equalsIgnoreCase(w, f)));
  }

  private static void annotateExtensions(Extensions extensions, DomElementAnnotationHolder holder) {
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
        new AddDomElementQuickFix<>(extensions.getDefaultExtensionNs()) {
          @Override
          public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            super.applyFix(project, descriptor);
            myElement.setStringValue(Extensions.DEFAULT_PREFIX);
          }
        });
    }
  }

  private static void annotateUnresolvedExtension(Extensions.UnresolvedExtension unresolvedExtension, DomElementAnnotationHolder holder) {
    final Module module = unresolvedExtension.getModule();
    if (module == null) return;

    final Extensions extensions = unresolvedExtension.getParentOfType(Extensions.class, true);
    assert extensions != null;
    String qualifiedExtensionId = extensions.getEpPrefix() + unresolvedExtension.getXmlElementName();

    final ExtensionPoint extensionPoint = ExtensionPointIndex.findExtensionPoint(module, qualifiedExtensionId);
    if (extensionPoint == null) {
      String message = new HtmlBuilder()
        .append(DevKitBundle.message("error.cannot.resolve.extension.point", qualifiedExtensionId))
        .nbsp()
        .append(HtmlChunk.link(DEPENDENCIES_DOC_URL, DevKitBundle.message("error.cannot.resolve.plugin.reference.link.title")))
        .wrapWith(HtmlChunk.html()).toString();

      holder.createProblem(unresolvedExtension, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, message, null);
      return;
    }


    final IdeaPlugin ideaPlugin = extensionPoint.getParentOfType(IdeaPlugin.class, true);
    assert ideaPlugin != null;
    String dependencyId = ideaPlugin.getPluginId();

    final LocalQuickFix addDependsFix = new LocalQuickFix() {
      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return DevKitBundle.message("error.cannot.resolve.extension.point.missing.dependency.fix.family.name");
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        DomElement domElement = DomUtil.getDomElement(descriptor.getPsiElement());
        if (domElement == null) return;

        final IdeaPlugin ideaPlugin = domElement.getParentOfType(IdeaPlugin.class, true);
        assert ideaPlugin != null;
        final Dependency dependency = ideaPlugin.addDependency();
        dependency.setStringValue(dependencyId);
      }
    };
    holder.createProblem(unresolvedExtension,
                         DevKitBundle.message("error.cannot.resolve.extension.point.missing.dependency", qualifiedExtensionId),
                         addDependsFix);
  }

  private static void annotateIdeaVersion(IdeaVersion ideaVersion, DomElementAnnotationHolder holder) {
    //noinspection deprecation
    highlightAttributeNotUsedAnymore(ideaVersion.getMin(), holder);
    //noinspection deprecation
    highlightAttributeNotUsedAnymore(ideaVersion.getMax(), holder);
    highlightUntilBuild(ideaVersion, holder);

    GenericAttributeValue<BuildNumber> sinceBuild = ideaVersion.getSinceBuild();
    GenericAttributeValue<BuildNumber> untilBuild = ideaVersion.getUntilBuild();
    if (!DomUtil.hasXml(sinceBuild) &&
        !DomUtil.hasXml(untilBuild)) {
      return;
    }

    BuildNumber sinceBuildNumber = sinceBuild.getValue();
    BuildNumber untilBuildNumber = untilBuild.getValue();
    if (sinceBuildNumber == null || untilBuildNumber == null) return;

    int compare = Comparing.compare(sinceBuildNumber, untilBuildNumber);
    if (compare > 0) {
      holder.createProblem(untilBuild, DevKitBundle.message("inspections.plugin.xml.until.build.must.be.greater.than.since.build"));
    }
  }

  private static void highlightUntilBuild(IdeaVersion ideaVersion, DomElementAnnotationHolder holder) {
    String untilBuild = ideaVersion.getUntilBuild().getStringValue();
    if (untilBuild != null && isStarSupported(ideaVersion.getSinceBuild().getStringValue())) {
      Matcher matcher = PluginManager.EXPLICIT_BIG_NUMBER_PATTERN.matcher(untilBuild);
      if (matcher.matches()) {
        holder.createProblem(
          ideaVersion.getUntilBuild(),
          DevKitBundle.message("inspections.plugin.xml.until.build.use.asterisk.instead.of.big.number", matcher.group(2)),
          new CorrectUntilBuildAttributeFix(PluginManager.convertExplicitBigNumberInUntilBuildToStar(untilBuild)));
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

  private static boolean isStarSupported(String buildNumber) {
    if (buildNumber == null) return false;
    Matcher matcher = Holder.BASE_LINE_EXTRACTOR.matcher(buildNumber);
    if (matcher.matches()) {
      int branch = Integer.parseInt(matcher.group(1));
      return branch >= FIRST_BRANCH_SUPPORTING_STAR;
    }
    return false;
  }

  private void annotateExtension(Extension extension, DomElementAnnotationHolder holder) {
    final ExtensionPoint extensionPoint = extension.getExtensionPoint();
    if (extensionPoint == null) return;
    final Module module = extension.getModule();
    final String effectiveQualifiedName = extensionPoint.getEffectiveQualifiedName();

    annotateExtensionPointStatus(holder, extension, extensionPoint, effectiveQualifiedName, module);
    annotateErrorHandler(holder, extension, effectiveQualifiedName, module);

    for (DomAttributeChildDescription<?> attributeDescription : extension.getGenericInfo().getAttributeChildrenDescriptions()) {
      final GenericAttributeValue<?> attributeValue = attributeDescription.getDomAttributeValue(extension);
      if (attributeValue == null || !DomUtil.hasXml(attributeValue)) continue;

      // IconsReferencesContributor
      if ("icon".equals(attributeDescription.getXmlElementName())) {
        annotateResolveProblems(holder, attributeValue);
      }
      else if ("order".equals(attributeDescription.getXmlElementName())) {
        annotateOrderAttributeProblems(holder, attributeValue);
      }
      annotateReferencedFieldStatus(holder, extension, attributeDescription, attributeValue, module);
    }
  }

  private void annotateExtensionPointStatus(DomElementAnnotationHolder holder,
                                            Extension extension,
                                            ExtensionPoint extensionPoint,
                                            String effectiveQualifiedName,
                                            @Nullable Module module) {
    final ExtensionPoint.Status status = extensionPoint.getExtensionPointStatus();
    ExtensionPoint.Status.Kind kind = status.getKind();
    if (kind == ExtensionPoint.Status.Kind.SCHEDULED_FOR_REMOVAL_API) {
      final String inVersion = status.getAdditionalData();
      String message = inVersion == null ?
                       DevKitBundle.message("inspections.plugin.xml.deprecated.ep.marked.for.removal",
                                            effectiveQualifiedName) :
                       DevKitBundle.message("inspections.plugin.xml.deprecated.ep.marked.for.removal.in.version",
                                            effectiveQualifiedName, inVersion);
      highlightDeprecatedMarkedForRemoval(extension, message, holder, false);
    }
    else if (kind == ExtensionPoint.Status.Kind.DEPRECATED) {
      highlightDeprecated(
        extension, DevKitBundle.message("inspections.plugin.xml.deprecated.ep", effectiveQualifiedName),
        holder, false, false);
    }
    else if (kind == ExtensionPoint.Status.Kind.ADDITIONAL_DEPRECATED && module != null) {
      final String knownReplacementEp = status.getAdditionalData();
      if (knownReplacementEp == null) {
        highlightDeprecated(
          extension, DevKitBundle.message("inspections.plugin.xml.deprecated.ep", effectiveQualifiedName),
          holder, false, false);
      }
      else if (ExtensionPointIndex.findExtensionPoint(module, knownReplacementEp) != null) {
        highlightDeprecated(
          extension,
          DevKitBundle.message("inspections.plugin.xml.deprecated.ep.use.replacement", effectiveQualifiedName, knownReplacementEp),
          holder, false, false);
      }
    }
    else if (kind == ExtensionPoint.Status.Kind.OBSOLETE) {
      highlightObsolete(extension, holder);
    }
    else if (kind == ExtensionPoint.Status.Kind.EXPERIMENTAL_API) {
      if (module != null) {
        boolean fromSameProject = Optional.ofNullable(extension.getExtensionPoint())
          .map(ExtensionPoint::getEffectiveClass)
          .map(PsiElement::getContainingFile)
          .map(PsiFile::getVirtualFile)
          .map(file -> projectScope(module.getProject()).contains(file))
          .orElse(false);

        if (!myIgnoreUnstableApiDeclaredInThisProject || !fromSameProject) {
          highlightExperimental(extension, holder);
        }
      }
      else {
        highlightExperimental(extension, holder);
      }
    }
    else {
      if (kind == ExtensionPoint.Status.Kind.INTERNAL_API &&
          module != null && !IntelliJProjectUtil.isIntelliJPlatformProject(module.getProject())) {
        highlightInternal(extension, holder);
      }
    }
  }

  private static void annotateErrorHandler(DomElementAnnotationHolder holder,
                                           Extension extension,
                                           String effectiveQualifiedName,
                                           @Nullable Module module) {
    if (ErrorReportSubmitter.EP_NAME.getName().equals(effectiveQualifiedName)) {
      String implementation = extension.getXmlTag().getAttributeValue("implementation");
      if (ITNReporter.class.getName().equals(implementation)) {
        IdeaPlugin plugin = extension.getParentOfType(IdeaPlugin.class, true);
        if (plugin != null) {
          Vendor vendor = plugin.getVendor();
          if (DomUtil.hasXml(vendor) && PluginManagerCore.isDevelopedByJetBrains(vendor.getValue())) {
            highlightRedundant(extension,
                               DevKitBundle.message("inspections.plugin.xml.no.need.to.specify.itnReporter"),
                               ProblemHighlightType.LIKE_UNUSED_SYMBOL, holder);
          }
          else {
            boolean inPlatformCode = module != null && module.getName().startsWith("intellij.platform.");
            if (!inPlatformCode) {
              highlightRedundant(extension,
                                 DevKitBundle.message("inspections.plugin.xml.third.party.plugins.must.not.use.itnReporter"),
                                 holder);
            }
          }
        }
      }
    }
  }

  private static void annotateReferencedFieldStatus(DomElementAnnotationHolder holder,
                                                    Extension extension,
                                                    DomAttributeChildDescription<?> attributeDescription,
                                                    GenericAttributeValue<?> attributeValue,
                                                    @Nullable Module module) {
    final PsiElement declaration = attributeDescription.getDeclaration(extension.getManager().getProject());
    if (declaration instanceof PsiField psiField) {
      if (psiField.isDeprecated()) {
        if (psiField.hasAnnotation(ApiStatus.ScheduledForRemoval.class.getCanonicalName())) {
          highlightDeprecatedMarkedForRemoval(attributeValue,
                                              DevKitBundle.message("inspections.plugin.xml.marked.for.removal.attribute",
                                                                   attributeDescription.getName()),
                                              holder, true);
        }
        else {
          highlightDeprecated(
            attributeValue, DevKitBundle.message("inspections.plugin.xml.deprecated.attribute", attributeDescription.getName()),
            holder, false, true);
        }
      }
      else if (psiField.hasAnnotation(ApiStatus.Experimental.class.getCanonicalName())) {
        highlightExperimental(attributeValue, holder);
      }
      else if (psiField.hasAnnotation(ApiStatus.Internal.class.getCanonicalName()) &&
               module != null && !IntelliJProjectUtil.isIntelliJPlatformProject(module.getProject())) {
        highlightInternal(attributeValue, holder);
      }
      else if (psiField.hasAnnotation(ApiStatus.Obsolete.class.getCanonicalName())) {
        highlightObsolete(attributeValue, holder);
      }
    }
  }

  private static void annotateComponent(Component component,
                                        DomElementAnnotationHolder holder) {
    GenericDomValue<PsiClass> implementationClassElement = component.getImplementationClass();
    PsiClass implementationClass = implementationClassElement.getValue();
    if (implementationClass != null) {
      if (implementationClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        holder.createProblem(implementationClassElement,
                             DevKitBundle.message("inspections.registration.problems.abstract"));
      }
    }

    GenericDomValue<PsiClass> interfaceClassElement = component.getInterfaceClass();
    PsiClass interfaceClass = interfaceClassElement.getValue();
    if (interfaceClass != null) {
      if (interfaceClass.equals(implementationClass) && component.getHeadlessImplementationClass().getValue() == null) {
        highlightRedundant(interfaceClassElement,
                           DevKitBundle.message("inspections.plugin.xml.component.interface.class.redundant"),
                           ProblemHighlightType.WARNING, holder);
      }
      IdeaPlugin plugin = component.getParentOfType(IdeaPlugin.class, false);
      if (plugin != null) {
        DuplicateComponentInterfaceChecker checker = new DuplicateComponentInterfaceChecker(component, interfaceClass);
        plugin.accept(checker);
        if (checker.declarationBeforeComponentFound) {
          holder.createProblem(interfaceClassElement,
                               DevKitBundle.message("inspections.registration.problems.component.duplicate.interface",
                                                    interfaceClass.getQualifiedName()));
        }
      }
    }

    if (implementationClass != null && interfaceClass != null &&
        implementationClass != interfaceClass && !implementationClass.isInheritor(interfaceClass, true)) {
      holder.createProblem(implementationClassElement,
                           DevKitBundle.message("inspections.registration.problems.component.incompatible.interface",
                                                interfaceClass.getQualifiedName()));
    }
  }

  private static void annotateVendor(Vendor vendor, DomElementAnnotationHolder holder) {
    //noinspection deprecation
    highlightAttributeNotUsedAnymore(vendor.getLogo(), holder);

    checkTemplateText(vendor, "YourCompany", holder);
    checkMaxLength(vendor, 255, holder);

    //noinspection HttpUrlsUsage
    checkTemplateText(vendor.getUrl(), "http://www.yourcompany.com", holder); // used in old template
    checkTemplateText(vendor.getUrl(), "https://www.yourcompany.com", holder);
    checkMaxLength(vendor.getUrl(), 255, holder);
    checkValidWebsite(vendor.getUrl(), holder);

    checkTemplateText(vendor.getEmail(), "support@yourcompany.com", holder);
    checkMaxLength(vendor.getEmail(), 255, holder);
  }

  private static void annotateProductDescriptor(ProductDescriptor productDescriptor, DomElementAnnotationHolder holder) {
    checkMaxLength(productDescriptor.getCode(), 15, holder);

    String releaseDate = productDescriptor.getReleaseDate().getValue();
    if (releaseDate != null && !isPlaceHolder(releaseDate)) {
      try {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
        dateFormat.setLenient(false);
        dateFormat.parse(releaseDate);
      }
      catch (ParseException e) {
        holder.createProblem(productDescriptor.getReleaseDate(),
                             DevKitBundle.message("inspections.plugin.xml.product.descriptor.invalid.date"));
      }
    }
    String version = productDescriptor.getReleaseVersion().getValue();
    if (version != null && !isPlaceHolder(version)) {
      try {
        Integer.parseInt(version);
      }
      catch (NumberFormatException e) {
        holder.createProblem(productDescriptor.getReleaseVersion(),
                             DevKitBundle.message("inspections.plugin.xml.product.descriptor.invalid.version"));
      }
    }
  }

  private static boolean isPlaceHolder(@Nullable String value) {
    return value != null && value.length() > 4 && value.startsWith("__") && value.endsWith("__");
  }

  private static void annotateAddToGroup(AddToGroup addToGroup, DomElementAnnotationHolder holder) {
    if (!DomUtil.hasXml(addToGroup.getRelativeToAction())) return;

    if (!DomUtil.hasXml(addToGroup.getAnchor())) {
      holder.createProblem(addToGroup, DevKitBundle.message("inspections.plugin.xml.anchor.must.have.relative-to-action"),
                           new AddDomElementQuickFix<>(addToGroup.getAnchor()));
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

    GenericAttributeValue<PsiClass> clazz = group.getClazz();
    if (DomUtil.hasXml(clazz) &&
        !DomUtil.hasXml(group.getId())) {
      holder.createProblem(group, ProblemHighlightType.WARNING, DevKitBundle.message("inspections.plugin.xml.action.group.id.required"),
                           null, new AddDomElementQuickFix<>(group.getId()));
    }


    GenericAttributeValue<ActionOrGroup> useShortcutOfAttribute = group.getUseShortcutOf();
    if (!DomUtil.hasXml(useShortcutOfAttribute)) return;

    if (!DomUtil.hasXml(clazz)) {
      holder.createProblem(group, DevKitBundle.message("inspections.plugin.xml.action.class.required.with.use.shortcut.of"),
                           new AddDomElementQuickFix<>(group.getClazz()));
      return;
    }

    PsiClass actionGroupClass = clazz.getValue();
    if (actionGroupClass == null) return;

    PsiClass actionGroup = JavaPsiFacade.getInstance(actionGroupClass.getProject()).findClass(ActionGroup.class.getName(),
                                                                                              actionGroupClass.getResolveScope());
    if (actionGroup == null) return;

    PsiMethod canBePerformedMethodTemplate = new LightMethodBuilder(actionGroupClass.getManager(), "canBePerformed")
      .setContainingClass(actionGroup)
      .setModifiers(PsiModifier.PUBLIC)
      .setMethodReturnType(PsiTypes.booleanType())
      .addParameter("context", DataContext.class.getName());

    PsiMethod actionGroupCanBePerformed = actionGroup.findMethodBySignature(canBePerformedMethodTemplate, false);
    if (actionGroupCanBePerformed == null) {
      return;
    }

    if (actionGroupClass.findMethodBySignature(canBePerformedMethodTemplate, false) == null) {
      String methodPresentation = PsiFormatUtil.formatMethod(canBePerformedMethodTemplate, PsiSubstitutor.EMPTY,
                                                             PsiFormatUtilBase.SHOW_NAME |
                                                             PsiFormatUtilBase.SHOW_PARAMETERS |
                                                             PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
                                                             PsiFormatUtilBase.SHOW_TYPE);
      holder.createProblem(clazz,
                           DevKitBundle.message("inspections.plugin.xml.action.must.override.method.with.use.shortcut.of",
                                                methodPresentation));
    }
  }

  private static void annotateAction(Action action,
                                     DomElementAnnotationHolder holder) {
    final GenericAttributeValue<String> iconAttribute = action.getIcon();
    if (DomUtil.hasXml(iconAttribute)) {
      annotateResolveProblems(holder, iconAttribute);
    }
  }

  private static void annotateSynonym(Synonym synonym, DomElementAnnotationHolder holder) {
    boolean hasKey = DomUtil.hasXml(synonym.getKey());
    boolean hasText = DomUtil.hasXml(synonym.getText());

    if (!hasKey && !hasText) {
      holder.createProblem(synonym, ProblemHighlightType.GENERIC_ERROR,
                           DevKitBundle.message("inspections.plugin.xml.synonym.missing.key.and.text"), null,
                           new AddDomElementQuickFix<>(synonym.getKey()),
                           new AddDomElementQuickFix<>(synonym.getText()));
    }
    else if (hasKey && hasText) {
      holder.createProblem(synonym, ProblemHighlightType.GENERIC_ERROR,
                           DevKitBundle.message("inspections.plugin.xml.synonym.both.key.and.text"), null);
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

  private static void annotateOrderAttributeProblems(DomElementAnnotationHolder holder, GenericAttributeValue<?> attributeValue) {
    String orderValue = attributeValue.getStringValue();
    if (StringUtil.isEmpty(orderValue)) {
      return;
    }

    try {
      LoadingOrder.readOrder(orderValue);
    }
    catch (AssertionError ignore) {
      holder.createProblem(attributeValue, HighlightSeverity.ERROR, DevKitBundle.message("inspections.plugin.xml.invalid.order.attribute"));
      return; // no need to resolve references for invalid attribute value
    }

    annotateResolveProblems(holder, attributeValue);
  }

  private static void annotateResolveProblems(DomElementAnnotationHolder holder, GenericAttributeValue<?> attributeValue) {
    final XmlAttributeValue value = attributeValue.getXmlAttributeValue();
    if (value != null) {
      for (PsiReference reference : value.getReferences()) {
        if (reference.resolve() == null) {
          holder.createResolveProblem(attributeValue, reference);
        }
      }
    }
  }

  private static void highlightRedundant(DomElement element, @InspectionMessage String message, DomElementAnnotationHolder holder) {
    highlightRedundant(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, holder);
  }

  private static void highlightAttributeNotUsedAnymore(GenericAttributeValue<?> attributeValue,
                                                       DomElementAnnotationHolder holder) {
    if (!DomUtil.hasXml(attributeValue)) return;
    highlightDeprecated(attributeValue,
                        DevKitBundle.message("inspections.plugin.xml.attribute.not.used.anymore", attributeValue.getXmlElementName()),
                        holder, true, true);
  }

  private static void highlightDeprecated(DomElement element, @InspectionMessage String message, DomElementAnnotationHolder holder,
                                          boolean useRemoveQuickfix, boolean highlightWholeElement) {
    doHighlightDeprecatedElement(element, message, holder, useRemoveQuickfix, highlightWholeElement, false);
  }

  private static void highlightDeprecatedMarkedForRemoval(DomElement element, @InspectionMessage String message,
                                                          DomElementAnnotationHolder holder,
                                                          boolean highlightWholeElement) {
    doHighlightDeprecatedElement(element, message, holder, false, highlightWholeElement, true);
  }

  private static void doHighlightDeprecatedElement(DomElement element,
                                                   @InspectionMessage String message,
                                                   DomElementAnnotationHolder holder,
                                                   boolean useRemoveQuickfix,
                                                   boolean highlightWholeElement,
                                                   boolean forRemoval) {
    DomElementProblemDescriptor problem;
    ProblemHighlightType problemHighlightType = forRemoval ? ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL :
                                                ProblemHighlightType.LIKE_DEPRECATED;
    if (!useRemoveQuickfix) {
      problem = holder.createProblem(element, problemHighlightType, message, null);
    }
    else {
      problem = holder.createProblem(element, problemHighlightType, message, null, new RemoveDomElementQuickFix(element));
    }
    if (highlightWholeElement) {
      problem.highlightWholeElement();
    }
  }

  @SuppressWarnings("unused") // might be useful again later
  private static void highlightJetbrainsOnly(DomElement element, DomElementAnnotationHolder holder) {
    holder.createProblem(element, ProblemHighlightType.WARNING,
                         DevKitBundle.message("inspections.plugin.xml.jetbrains.only.api",
                                              ApiStatus.Experimental.class.getCanonicalName()),
                         null)
      .highlightWholeElement();
  }

  private static void highlightExperimental(DomElement element, DomElementAnnotationHolder holder) {
    holder.createProblem(element, ProblemHighlightType.WARNING,
                         DevKitBundle.message("inspections.plugin.xml.usage.of.experimental.api",
                                              ApiStatus.Experimental.class.getCanonicalName()),
                         null)
      .highlightWholeElement();
  }

  private static void highlightInternal(DomElement element, DomElementAnnotationHolder holder) {
    holder.createProblem(element, ProblemHighlightType.WARNING,
                         DevKitBundle.message("inspections.plugin.xml.usage.of.internal.api",
                                              ApiStatus.Internal.class.getCanonicalName()),
                         null)
      .highlightWholeElement();
  }

  private static void highlightObsolete(DomElement element, DomElementAnnotationHolder holder) {
    holder.createProblem(element, ProblemHighlightType.LIKE_DEPRECATED,
                         DevKitBundle.message("inspections.plugin.xml.usage.of.obsolete.api",
                                              ApiStatus.Obsolete.class.getCanonicalName()),
                         null)
      .highlightWholeElement();
  }

  private static void checkTemplateText(GenericDomValue<String> domValue,
                                        @NonNls String templateText,
                                        DomElementAnnotationHolder holder) {
    if (templateText.equals(domValue.getValue())) {
      holder.createProblem(domValue, DevKitBundle.message("inspections.plugin.xml.do.not.use.template.text", templateText));
    }
  }

  private static void checkTemplateTextContains(GenericDomValue<String> domValue,
                                                @NonNls String containsText,
                                                DomElementAnnotationHolder holder) {
    String text = domValue.getStringValue();
    if (text != null && StringUtil.containsIgnoreCase(text, containsText)) {
      holder.createProblem(domValue, DevKitBundle.message("inspections.plugin.xml.must.not.contain.template.text", containsText));
    }
  }

  private static void checkTemplateTextContainsWord(GenericDomValue<String> domValue,
                                                    DomElementAnnotationHolder holder,
                                                    @NonNls String... templateWords) {
    String text = domValue.getStringValue();
    if (text == null) return;
    for (String word : StringUtil.getWordsIn(text)) {
      for (String templateWord : templateWords) {
        if (StringUtil.equalsIgnoreCase(word, templateWord)) {
          holder.createProblem(domValue, DevKitBundle.message("inspections.plugin.xml.must.not.contain.template.text", templateWord));
        }
      }
    }
  }

  private static void checkMaxLength(GenericDomValue<String> domValue,
                                     int maxLength,
                                     DomElementAnnotationHolder holder) {
    String value = domValue.getStringValue();
    if (value != null && value.length() > maxLength) {
      holder.createProblem(domValue, DevKitBundle.message("inspections.plugin.xml.value.exceeds.max.length", maxLength));
    }
  }

  private static void checkHasRealText(GenericDomValue<String> domValue,
                                       DomElementAnnotationHolder holder) {
    if (!DomUtil.hasXml(domValue)) return;

    String value = StringUtil.removeHtmlTags(StringUtil.notNullize(domValue.getStringValue()));
    value = StringUtil.replace(value, CommonXmlStrings.CDATA_START, "");
    value = StringUtil.replace(value, CommonXmlStrings.CDATA_END, "");

    if (StringUtil.isEmptyOrSpaces(value) || value.length() < MINIMAL_DESCRIPTION_LENGTH) {
      holder.createProblem(domValue,
                           DevKitBundle.message("inspections.plugin.xml.value.must.have.minimum.length", MINIMAL_DESCRIPTION_LENGTH));
    }
  }

  @SuppressWarnings("HttpUrlsUsage")
  private static void checkValidWebsite(@NotNull GenericDomValue<String> domValue,
                                        DomElementAnnotationHolder holder) {
    if (!DomUtil.hasXml(domValue)) return;

    String url = domValue.getStringValue();
    if (StringUtil.isEmptyOrSpaces(url) ||
        (!url.startsWith("https://") && !url.startsWith("http://"))) {
      holder.createProblem(domValue, DevKitBundle.message("inspections.plugin.xml.value.must.be.https.or.http.link.to.website"));
    }
  }

  private static class CorrectUntilBuildAttributeFix implements LocalQuickFix {
    private static final Logger LOG = Logger.getInstance(PluginXmlDomInspection.class);

    private final String myCorrectValue;

    CorrectUntilBuildAttributeFix(String correctValue) {
      myCorrectValue = correctValue;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return DevKitBundle.message("inspections.plugin.xml.change.until.build.name", myCorrectValue);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return DevKitBundle.message("inspections.plugin.xml.change.until.build.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final XmlAttribute attribute = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), XmlAttribute.class, false);
      GenericAttributeValue<?> domElement = DomManager.getDomManager(project).getDomElement(attribute);
      LOG.assertTrue(domElement != null);
      domElement.setStringValue(myCorrectValue);
    }
  }

  private static final class AddMissingMainTag implements LocalQuickFix {

    @IntentionFamilyName
    @NotNull
    private final String myFamilyName;

    @NotNull
    private final String myTagName;

    @Nullable
    private final String myTagValue;

    private AddMissingMainTag(@IntentionFamilyName @NotNull String familyName,
                              @NotNull GenericDomValue<String> domValue,
                              @Nullable String tagValue) {
      myFamilyName = familyName;
      myTagName = domValue.getXmlElementName();
      myTagValue = tagValue;
    }

    @IntentionFamilyName
    @NotNull
    @Override
    public String getFamilyName() {
      return myFamilyName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiFile file = descriptor.getPsiElement().getContainingFile();
      IdeaPlugin root = DescriptorUtil.getIdeaPlugin((XmlFile)file);
      if (root == null) return;
      XmlTag rootTag = root.getXmlTag();
      if (rootTag == null) return;
      XmlTag after = getLastSubTag(rootTag, root.getId(), root.getDescription(), root.getVersion(), root.getName());
      XmlTag missingTag = rootTag.createChildTag(myTagName, rootTag.getNamespace(), myTagValue, false);

      XmlTag addedTag;
      if (after == null) {
        addedTag = rootTag.addSubTag(missingTag, true);
      }
      else {
        addedTag = (XmlTag)rootTag.addAfter(missingTag, after);
      }

      if (StringUtil.isEmpty(myTagValue) && !IntentionPreviewUtils.isPreviewElement(descriptor.getPsiElement())) {
        int valueStartOffset = addedTag.getValue().getTextRange().getStartOffset();
        NavigatableAdapter.navigate(project, file.getVirtualFile(), valueStartOffset, true);
      }
    }

    private static XmlTag getLastSubTag(@NotNull XmlTag rootTag, DomElement... children) {
      Set<XmlTag> childrenTags = new HashSet<>();
      for (DomElement child : children) {
        if (child != null) {
          childrenTags.add(child.getXmlTag());
        }
      }
      XmlTag[] subTags = rootTag.getSubTags();
      for (int i = subTags.length - 1; i >= 0; i--) {
        if (childrenTags.contains(subTags[i])) {
          return subTags[i];
        }
      }
      return null;
    }
  }

  private static class DuplicateComponentInterfaceChecker implements DomElementVisitor {
    private final Component component;
    private final PsiClass componentInterfaceClass;
    private final int componentTextOffset;
    private String componentType;
    private boolean declarationBeforeComponentFound = false;

    private DuplicateComponentInterfaceChecker(@NotNull Component component, @NotNull PsiClass componentInterfaceClass) {
      this.component = component;
      this.componentInterfaceClass = componentInterfaceClass;
      XmlElement componentElement = component.getXmlElement();
      this.componentTextOffset = componentElement != null ? componentElement.getTextOffset() : -1;
    }

    @Override
    public void visitDomElement(DomElement element) {
      element.acceptChildren(this);
      if (!declarationBeforeComponentFound && element instanceof Component checkedComponent
          && isFirstDuplicateDeclaration(checkedComponent)) {
        declarationBeforeComponentFound = true;
      }
    }

    private boolean isFirstDuplicateDeclaration(Component checkedComponent) {
      if (checkedComponent == component) return false;
      XmlElement element = checkedComponent.getXmlElement();
      if (element != null && element.getTextOffset() >= componentTextOffset) return false; // do not check after component's offset
      if (componentLevelsEqual(component, checkedComponent)) {
        if (componentInterfaceClass.equals(checkedComponent.getInterfaceClass().getValue())) {
          if (component instanceof Component.Module) {
            if (componentType == null) {
              componentType = getComponentType(component);
            }
            String checkedComponentType = getComponentType(checkedComponent);
            return Objects.equals(componentType, checkedComponentType);
          }
          return true;
        }
      }
      return false;
    }

    private static boolean componentLevelsEqual(Component component, Component checkedComponent) {
      return (component instanceof Component.Application && checkedComponent instanceof Component.Application) ||
             (component instanceof Component.Project && checkedComponent instanceof Component.Project) ||
             (component instanceof Component.Module && checkedComponent instanceof Component.Module);
    }

    private static String getComponentType(Component component) {
      return component.getOptions().stream()
        .filter(option -> "type".equals(option.getName().getValue()))
        .map(Option::getValue)
        .map(GenericValue::getValue)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
    }
  }
}
