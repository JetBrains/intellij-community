// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.ExtensionPoints;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.diagnostic.ITNReporter;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.openapi.ui.panel.PanelGridBuilder;
import com.intellij.openapi.util.AtomicClearableLazyValue;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.UI;
import com.intellij.util.xml.*;
import com.intellij.util.xml.highlighting.*;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.xml.CommonXmlStrings;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Action;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;
import org.jetbrains.idea.devkit.inspections.quickfix.AddWithTagFix;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PluginXmlDomInspection extends BasicDomElementsInspection<IdeaPlugin> {
  private static final Logger LOG = Logger.getInstance(PluginXmlDomInspection.class);

  @NonNls
  private static final String PLUGIN_ICON_SVG_FILENAME = "pluginIcon.svg";

  public List<String> myRegistrationCheckIgnoreClassList = new ExternalizableStringSet();

  @XCollection
  public List<PluginModuleSet> PLUGINS_MODULES = new ArrayList<>();
  private final AtomicClearableLazyValue<Map<String, PluginModuleSet>> myPluginModuleSetByModuleName = AtomicClearableLazyValue.create(() -> {
    Map<String, PluginModuleSet> result = new HashMap<>();
    for (PluginModuleSet modulesSet : PLUGINS_MODULES) {
      for (String module : modulesSet.modules) {
        result.put(module, modulesSet);
      }
    }
    return result;
  });


  public PluginXmlDomInspection() {
    super(IdeaPlugin.class);
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    ListTable table = new ListTable(new ListWrappingTableModel(myRegistrationCheckIgnoreClassList, ""));
    JPanel panel = UiUtils.createAddRemoveTreeClassChooserPanel(table, DevKitBundle.message("inspections.plugin.xml.add.ignored.class.title"));
    PanelGridBuilder grid = UI.PanelFactory.grid();
    grid.resize().add(
      UI.PanelFactory.panel(panel)
        .withLabel(DevKitBundle.message("inspections.plugin.xml.ignore.classes.title"))
        .moveLabelOnTop()
        .resizeY(true)
    );

    if (ApplicationManager.getApplication().isInternal()) {
      JBTextArea component = new JBTextArea(5, 80);
      component.setText(PLUGINS_MODULES.stream().map(it -> String.join(",", it.modules)).collect(Collectors.joining("\n")));
      component.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          PLUGINS_MODULES.clear();
          for (String line : StringUtil.splitByLines(component.getText())) {
            PluginModuleSet set = new PluginModuleSet();
            set.modules = new LinkedHashSet<>(StringUtil.split(line, ","));
            PLUGINS_MODULES.add(set);
          }
          myPluginModuleSetByModuleName.drop();
        }
      });
      ComponentPanelBuilder pluginModulesPanel =
        UI.PanelFactory.panel(new JBScrollPane(component))
          .withLabel(DevKitBundle.message("inspections.plugin.xml.plugin.modules.label"))
          .moveLabelOnTop()
          .withComment(DevKitBundle.message("inspections.plugin.xml.plugin.modules.description"))
          .resizeY(true);
      grid.add(pluginModulesPanel);
    }

    return grid.createPanel();
  }

  @Override
  public void readSettings(@NotNull Element node) {
    super.readSettings(node);
    myPluginModuleSetByModuleName.drop();
  }

  @Override
  @NotNull
  public String getShortName() {
    return "PluginXmlValidity";
  }

  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    super.checkDomElement(element, holder, helper);

    if (element instanceof IdeaPlugin) {
      Module module = element.getModule();
      if (module != null) {
        annotateIdeaPlugin((IdeaPlugin)element, holder, module);
        checkJetBrainsPlugin((IdeaPlugin)element, holder, module);
        checkPluginIcon((IdeaPlugin)element, holder, module);
      }
    }
    else {
      ComponentModuleRegistrationChecker componentModuleRegistrationChecker = new ComponentModuleRegistrationChecker(myPluginModuleSetByModuleName,
                                                                                                                     myRegistrationCheckIgnoreClassList,
                                                                                                                     holder);
      if (element instanceof Extension) {
        annotateExtension((Extension)element, holder, componentModuleRegistrationChecker);
      }
      else if (element instanceof ExtensionPoint) {
        annotateExtensionPoint((ExtensionPoint)element, holder, componentModuleRegistrationChecker);
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
        annotateComponent((Component)element, holder, componentModuleRegistrationChecker);
        if (element instanceof Component.Project) {
          annotateProjectComponent((Component.Project)element, holder);
        }
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

  private static void annotateIdeaPlugin(IdeaPlugin ideaPlugin, DomElementAnnotationHolder holder, @NotNull Module module) {
    //noinspection deprecation
    highlightAttributeNotUsedAnymore(ideaPlugin.getIdeaPluginVersion(), holder);
    //noinspection deprecation
    if (DomUtil.hasXml(ideaPlugin.getUseIdeaClassloader())) {
      //noinspection deprecation
      highlightDeprecated(ideaPlugin.getUseIdeaClassloader(), "Deprecated", holder, true, true);
    }

    checkMaxLength(ideaPlugin.getUrl(), 255, holder);

    checkMaxLength(ideaPlugin.getId(), 255, holder);

    checkTemplateText(ideaPlugin.getName(), "Plugin display name here", holder);
    checkTemplateTextContains(ideaPlugin.getName(), "plugin", holder);
    checkMaxLength(ideaPlugin.getName(), 255, holder);


    checkMaxLength(ideaPlugin.getDescription(), 65535, holder);
    checkHasRealText(ideaPlugin.getDescription(), 40, holder);
    checkTemplateTextContains(ideaPlugin.getDescription(), "Enter short description for your plugin here.", holder);
    checkTemplateTextContains(ideaPlugin.getDescription(), "most HTML tags may be used", holder);

    checkMaxLength(ideaPlugin.getChangeNotes(), 65535, holder);
    checkHasRealText(ideaPlugin.getChangeNotes(), 40, holder);
    checkTemplateTextContains(ideaPlugin.getChangeNotes(), "Add change notes here", holder);
    checkTemplateTextContains(ideaPlugin.getChangeNotes(), "most HTML tags may be used", holder);

    if (!hasRealPluginId(ideaPlugin)) return;

    boolean isNotIdeaProject = !PsiUtil.isIdeaProject(module.getProject());

    if (isNotIdeaProject &&
        !DomUtil.hasXml(ideaPlugin.getVersion()) &&
        PluginModuleType.isOfType(module)) {
      holder.createProblem(ideaPlugin, DevKitBundle.message("inspections.plugin.xml.version.must.be.specified"),
                           new AddMissingMainTag("Add <version>", ideaPlugin.getVersion(), ""));
    }
    checkMaxLength(ideaPlugin.getVersion(), 64, holder);


    if (isNotIdeaProject && !DomUtil.hasXml(ideaPlugin.getVendor())) {
      holder.createProblem(ideaPlugin, DevKitBundle.message("inspections.plugin.xml.vendor.must.be.specified"),
                           new AddMissingMainTag("Add <vendor>", ideaPlugin.getVendor(), ""));
    }
  }

  private static void checkJetBrainsPlugin(IdeaPlugin ideaPlugin, DomElementAnnotationHolder holder, @NotNull Module module) {
    if (!PsiUtil.isIdeaProject(module.getProject())) return;

    if (!hasRealPluginId(ideaPlugin)) return;

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
                           new AddMissingMainTag("Specify JetBrains as vendor", vendor, PluginManagerMain.JETBRAINS_VENDOR));
    }
    else if (!PluginManagerMain.isDevelopedByJetBrains(vendor.getValue())) {
      holder.createProblem(vendor, DevKitBundle.message("inspections.plugin.xml.plugin.should.include.jetbrains.vendor"));
    }
  }

  private static void checkPluginIcon(IdeaPlugin ideaPlugin, DomElementAnnotationHolder holder, Module module) {
    if (!hasRealPluginId(ideaPlugin)) return;

    Collection<VirtualFile> pluginIconFiles =
      FilenameIndex.getVirtualFilesByName(module.getProject(), PLUGIN_ICON_SVG_FILENAME, GlobalSearchScope.moduleScope(module));
    if (pluginIconFiles.isEmpty()) {
      holder.createProblem(ideaPlugin, ProblemHighlightType.WEAK_WARNING,
                           DevKitBundle.message("inspections.plugin.xml.no.plugin.icon.svg.file", PLUGIN_ICON_SVG_FILENAME),
                           null);
    }
  }

  private static boolean hasRealPluginId(IdeaPlugin ideaPlugin) {
    String pluginId = ideaPlugin.getPluginId();
    return pluginId != null && !pluginId.equals(PluginManagerCore.CORE_PLUGIN_ID);
  }

  private void annotateExtensionPoint(ExtensionPoint extensionPoint,
                                      DomElementAnnotationHolder holder,
                                      ComponentModuleRegistrationChecker componentModuleRegistrationChecker) {
    if (extensionPoint.getWithElements().isEmpty() &&
        !extensionPoint.collectMissingWithTags().isEmpty()) {
      holder.createProblem(extensionPoint, DevKitBundle.message("inspections.plugin.xml.ep.doesnt.have.with"), new AddWithTagFix());
    }

    checkEpBeanClassAndInterface(extensionPoint, holder);
    checkEpNameAndQualifiedName(extensionPoint, holder);

    Module module = extensionPoint.getModule();
    if (componentModuleRegistrationChecker.isIdeaPlatformModule(module)) {
      componentModuleRegistrationChecker.checkProperModule(extensionPoint);
    }
  }

  private static void checkEpBeanClassAndInterface(ExtensionPoint extensionPoint, DomElementAnnotationHolder holder) {
    boolean hasBeanClass = DomUtil.hasXml(extensionPoint.getBeanClass());
    boolean hasInterface = DomUtil.hasXml(extensionPoint.getInterface());
    if (hasBeanClass && hasInterface) {
      holder.createProblem(extensionPoint, ProblemHighlightType.GENERIC_ERROR,
                           DevKitBundle.message("inspections.plugin.xml.ep.both.beanClass.and.interface"), null);
    }
    else if (!hasBeanClass && !hasInterface) {
      holder.createProblem(extensionPoint, ProblemHighlightType.GENERIC_ERROR,
                           DevKitBundle.message("inspections.plugin.xml.ep.missing.beanClass.and.interface"), null);
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

    if (StringUtil.isEmpty(name) ||
        !Character.isLowerCase(name.charAt(0)) || // also checks that name doesn't start with dot
        name.toUpperCase().equals(name) || // not all uppercase
        !StringUtil.isLatinAlphanumeric(name.replace(".", "")) ||
        name.charAt(name.length() - 1) == '.') {
      return false;
    }

    List<String> fragments = StringUtil.split(name, ".");
    if (fragments.stream().anyMatch(f -> Character.isUpperCase(f.charAt(0)))) {
      return false;
    }

    String epName = fragments.get(fragments.size() - 1);
    fragments.remove(fragments.size() - 1);
    List<String> words = StringUtil.getWordsIn(epName);
    return words.stream().noneMatch(w -> fragments.stream().anyMatch(f -> StringUtil.equalsIgnoreCase(w, f)));
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

    GenericAttributeValue<String> sinceBuild = ideaVersion.getSinceBuild();
    GenericAttributeValue<String> untilBuild = ideaVersion.getUntilBuild();
    if (!DomUtil.hasXml(sinceBuild) ||
        !DomUtil.hasXml(untilBuild)) {
      return;
    }

    BuildNumber sinceBuildNumber = parseBuildNumber(sinceBuild, holder);
    BuildNumber untilBuildNumber = parseBuildNumber(untilBuild, holder);
    if (sinceBuildNumber == null || untilBuildNumber == null) return;

    int compare = Comparing.compare(sinceBuildNumber, untilBuildNumber);
    if (compare > 0) {
      holder.createProblem(untilBuild, DevKitBundle.message("inspections.plugin.xml.until.build.must.be.greater.than.since.build"));
    }
  }

  @Nullable
  private static BuildNumber parseBuildNumber(GenericAttributeValue<String> build,
                                              DomElementAnnotationHolder holder) {
    try {
      return BuildNumber.fromString(build.getStringValue());
    }
    catch (RuntimeException e) {
      holder.createProblem(build, DevKitBundle.message("inspections.plugin.xml.until.since.build.invalid"));
      return null;
    }
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

  private void annotateExtension(Extension extension,
                                 DomElementAnnotationHolder holder, ComponentModuleRegistrationChecker componentModuleRegistrationChecker) {
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
          LocalQuickFix fix = new RemoveDomElementQuickFix(extension);
          if (DomUtil.hasXml(vendor) && PluginManagerMain.isDevelopedByJetBrains(vendor.getValue())) {
            holder.createProblem(extension, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                 DevKitBundle.message("inspections.plugin.xml.no.need.to.specify.itnReporter"),
                                 null, fix).highlightWholeElement();
          }
          else {
            Module module = plugin.getModule();
            boolean inPlatformCode = module != null && module.getName().startsWith("intellij.platform.");
            if (!inPlatformCode) {
              holder.createProblem(extension, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                   DevKitBundle.message("inspections.plugin.xml.third.party.plugins.must.not.use.itnReporter"),
                                   null, fix).highlightWholeElement();
            }
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
    if (componentModuleRegistrationChecker.isIdeaPlatformModule(module)) {
      componentModuleRegistrationChecker.checkProperXmlFileForExtension(extension);
    }
  }

  private void annotateComponent(Component component,
                                 DomElementAnnotationHolder holder, ComponentModuleRegistrationChecker componentModuleRegistrationChecker) {
    Module module = component.getModule();
    if (componentModuleRegistrationChecker.isIdeaPlatformModule(module)) {
      componentModuleRegistrationChecker.checkProperXmlFileForClass(
        component, component.getImplementationClass().getValue());
    }

    GenericDomValue<PsiClass> interfaceClassElement = component.getInterfaceClass();
    PsiClass interfaceClass = interfaceClassElement.getValue();
    if (interfaceClass != null && interfaceClass.equals(component.getImplementationClass().getValue())) {
      DomElementProblemDescriptor problem = holder.createProblem(
        interfaceClassElement,
        ProblemHighlightType.WARNING,
        DevKitBundle.message("inspections.plugin.xml.component.interface.class.redundant"),
        null,
        new RemoveDomElementQuickFix(interfaceClassElement));
      problem.highlightWholeElement();
    }
  }

  private static void annotateVendor(Vendor vendor, DomElementAnnotationHolder holder) {
    //noinspection deprecation
    highlightAttributeNotUsedAnymore(vendor.getLogo(), holder);

    checkTemplateText(vendor, "YourCompany", holder);
    checkMaxLength(vendor, 255, holder);

    checkTemplateText(vendor.getUrl(), "http://www.yourcompany.com", holder);
    checkMaxLength(vendor.getUrl(), 255, holder);

    checkTemplateText(vendor.getEmail(), "support@yourcompany.com", holder);
    checkMaxLength(vendor.getEmail(), 255, holder);
  }

  private static void annotateProductDescriptor(ProductDescriptor productDescriptor, DomElementAnnotationHolder holder) {
    checkMaxLength(productDescriptor.getCode(), 15, holder);

    String releaseDate = productDescriptor.getReleaseDate().getValue();
    if (releaseDate == null) return;

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

    GenericAttributeValue<ActionOrGroup> useShortcutOfAttribute = group.getUseShortcutOf();
    if (!DomUtil.hasXml(useShortcutOfAttribute)) return;

    GenericAttributeValue<PsiClass> clazz = group.getClazz();
    if (!DomUtil.hasXml(clazz)) {
      holder.createProblem(group, "'class' must be specified with 'use-shortcut-of'",
                           new AddDomElementQuickFix<GenericAttributeValue>(group.getClazz()));
      return;
    }

    PsiClass actionGroupClass = clazz.getValue();
    if (actionGroupClass == null) return;

    PsiMethod canBePerformedMethod = new LightMethodBuilder(actionGroupClass.getManager(), "canBePerformed")
      .setContainingClass(JavaPsiFacade.getInstance(actionGroupClass.getProject()).findClass(ActionGroup.class.getName(),
                                                                                             actionGroupClass.getResolveScope()))
      .setModifiers(PsiModifier.PUBLIC)
      .setMethodReturnType(PsiType.BOOLEAN)
      .addParameter("context", DataContext.class.getName());

    PsiMethod overriddenCanBePerformedMethod = actionGroupClass.findMethodBySignature(canBePerformedMethod, false);
    if (overriddenCanBePerformedMethod == null) {
      String methodPresentation = PsiFormatUtil.formatMethod(canBePerformedMethod, PsiSubstitutor.EMPTY,
                                                             PsiFormatUtilBase.SHOW_NAME |
                                                             PsiFormatUtilBase.SHOW_PARAMETERS |
                                                             PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
                                                             PsiFormatUtilBase.SHOW_TYPE);
      holder.createProblem(clazz, "Must override " + methodPresentation + " with 'use-shortcut-of'");
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
    }
    catch (AssertionError ignore) {
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

  private static void highlightAttributeNotUsedAnymore(GenericAttributeValue attributeValue,
                                                       DomElementAnnotationHolder holder) {
    if (!DomUtil.hasXml(attributeValue)) return;
    highlightDeprecated(
      attributeValue, DevKitBundle.message("inspections.plugin.xml.attribute.not.used.anymore", attributeValue.getXmlElementName()),
      holder, true, true);
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

  private static void checkTemplateText(GenericDomValue<String> domValue,
                                        String templateText,
                                        DomElementAnnotationHolder holder) {
    if (templateText.equals(domValue.getValue())) {
      holder.createProblem(domValue, DevKitBundle.message("inspections.plugin.xml.do.not.use.template.text", templateText));
    }
  }

  private static void checkTemplateTextContains(GenericDomValue<String> domValue,
                                                String containsText,
                                                DomElementAnnotationHolder holder) {
    String text = domValue.getStringValue();
    if (text != null && StringUtil.containsIgnoreCase(text, containsText)) {
      holder.createProblem(domValue, DevKitBundle.message("inspections.plugin.xml.must.not.contain.template.text", containsText));
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
                                       int minimumLength,
                                       DomElementAnnotationHolder holder) {
    if (!DomUtil.hasXml(domValue)) return;

    String value = StringUtil.removeHtmlTags(StringUtil.notNullize(domValue.getStringValue()));
    value = StringUtil.replace(value, CommonXmlStrings.CDATA_START, "");
    value = StringUtil.replace(value, CommonXmlStrings.CDATA_END, "");

    if (StringUtil.isEmptyOrSpaces(value) || value.length() < minimumLength) {
      holder.createProblem(domValue, DevKitBundle.message("inspections.plugin.xml.value.must.have.minimum.length", minimumLength));
    }
  }

  private static class CorrectUntilBuildAttributeFix implements LocalQuickFix {
    private final String myCorrectValue;

    CorrectUntilBuildAttributeFix(String correctValue) {
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

  private static class AddMissingMainTag implements LocalQuickFix {

    @NotNull
    private final String myFamilyName;

    @NotNull
    private final String myTagName;

    @Nullable
    private final String myTagValue;

    private AddMissingMainTag(@NotNull String familyName, @NotNull GenericDomValue domValue, @Nullable String tagValue) {
      myFamilyName = familyName;
      myTagName = domValue.getXmlElementName();
      myTagValue = tagValue;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return myFamilyName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiFile file = descriptor.getPsiElement().getContainingFile();
      DomFileElement<IdeaPlugin> fileElement = DomManager.getDomManager(project).getFileElement((XmlFile)file, IdeaPlugin.class);
      if (fileElement != null) {
        IdeaPlugin root = fileElement.getRootElement();
        XmlTag after = getLastSubTag(root, root.getId(), root.getDescription(), root.getVersion(), root.getName());
        XmlTag rootTag = root.getXmlTag();
        XmlTag missingTag = rootTag.createChildTag(myTagName, rootTag.getNamespace(), myTagValue, false);

        XmlTag addedTag;
        if (after == null) {
          addedTag = rootTag.addSubTag(missingTag, true);
        }
        else {
          addedTag = (XmlTag)rootTag.addAfter(missingTag, after);
        }

        if (StringUtil.isEmpty(myTagValue)) {
          int valueStartOffset = addedTag.getValue().getTextRange().getStartOffset();
          NavigatableAdapter.navigate(project, file.getVirtualFile(), valueStartOffset, true);
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

  @Tag("modules-set")
  public static class PluginModuleSet {
    @XCollection(elementName = "module", valueAttributeName = "name")
    @Property(surroundWithTag = false)
    public Set<String> modules = new LinkedHashSet<>();
  }
}
