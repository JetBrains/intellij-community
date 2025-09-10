// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiPackage;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.impl.IdeaPluginPackageConverter;

import java.util.List;

@DefinesXml
@Presentation(icon = "AllIcons.Nodes.Plugin", typeName = DevkitDomPresentationConstants.PLUGIN)
@Stubbed
public interface IdeaPlugin extends DomElement {
  @NonNls String TAG_NAME = "idea-plugin";

  @Nullable @NlsSafe String getPluginId();

  @NotNull ContentDescriptor getFirstOrAddContentDescriptor();

  default boolean hasRealPluginId() {
    String pluginId = getPluginId();
    return pluginId != null && !pluginId.equals(PluginManagerCore.CORE_PLUGIN_ID);
  }

  default boolean isV2Descriptor() {
    return DomUtil.hasXml(getPackage()) ||
           !getContent().isEmpty() ||
           DomUtil.hasXml(getDependencies());
  }

  @SubTag("product-descriptor")
  @NotNull ProductDescriptor getProductDescriptor();

  @SubTagList("content")
  @Stubbed
  @NotNull List<? extends ContentDescriptor> getContent();

  @SubTagList("content")
  ContentDescriptor addContent();

  @SubTag("dependencies")
  @Stubbed
  @NotNull DependencyDescriptor getDependencies();

  @NameValue
  @Stubbed
  @NotNull GenericDomValue<String> getId();

  /**
   * @deprecated Unused.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Attribute("version")
  @Deprecated
  @NotNull GenericAttributeValue<Integer> getIdeaPluginVersion();

  @NotNull GenericAttributeValue<String> getUrl();

  /**
   * @deprecated Unused.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @NotNull GenericAttributeValue<Boolean> getUseIdeaClassloader();

  @NotNull GenericAttributeValue<Boolean> getAllowBundledUpdate();

  /**
   * @deprecated Will be dropped without a replacement: use either a regular plugin or implement a product module.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Stubbed
  @Deprecated
  @NotNull GenericAttributeValue<Boolean> getImplementationDetail();

  @NotNull GenericAttributeValue<Boolean> getRequireRestart();

  @Stubbed
  @Convert(IdeaPluginPackageConverter.class)
  @NotNull GenericAttributeValue<PsiPackage> getPackage();

  @Stubbed
  @Required(false)
  @NotNull GenericDomValue<String> getName();

  @NotNull GenericDomValue<String> getDescription();

  @Required(false)
  @NotNull GenericDomValue<String> getVersion();

  @NotNull Vendor getVendor();

  @NotNull GenericDomValue<String> getChangeNotes();

  @Stubbed
  @NotNull IdeaVersion getIdeaVersion();

  @NotNull GenericDomValue<String> getCategory();

  @Stubbed
  @NotNull GenericDomValue<String> getResourceBundle();

  @Stubbed
  @SubTagList("depends")
  @NotNull List<? extends Dependency> getDepends();

  @SubTagList("depends")
  Dependency addDependency();

  @SubTagList("incompatible-with")
  @NotNull List<GenericDomValue<String>> getIncompatibilities();

  @Stubbed
  @SubTagList("module")
  @NotNull List<? extends PluginModule> getModules();

  @SubTagList("extensions")
  @Stubbed
  @NotNull List<? extends Extensions> getExtensions();

  Extensions addExtensions();

  @Stubbed
  @SubTagList("extensionPoints")
  @NotNull List<? extends ExtensionPoints> getExtensionPoints();

  ExtensionPoints addExtensionPoints();

  @SubTagList("application-components")
  @NotNull List<? extends ApplicationComponents> getApplicationComponents();

  ApplicationComponents addApplicationComponents();

  @SubTagList("project-components")
  @NotNull List<? extends ProjectComponents> getProjectComponents();

  ProjectComponents addProjectComponents();

  @SubTagList("module-components")
  @NotNull List<? extends ModuleComponents> getModuleComponents();

  ModuleComponents addModuleComponents();

  @SubTagList("actions")
  @Stubbed
  @NotNull List<? extends Actions> getActions();

  Actions addActions();

  /**
   * Available since 192.
   */
  @SubTagList("applicationListeners")
  @NotNull List<? extends Listeners> getApplicationListeners();

  /**
   * Available since 192.
   */
  @SubTagList("projectListeners")
  @NotNull List<? extends Listeners> getProjectListeners();

  /**
   * @deprecated the corresponding tag in plugin.xml is not supported anymore, this method is used to highlight occurrences of such a tag
   */
  @SuppressWarnings("SpellCheckingInspection")
  @Deprecated
  @NotNull List<? extends Helpset> getHelpsets();
}
