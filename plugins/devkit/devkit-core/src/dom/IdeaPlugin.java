// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiPackage;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.ApiStatus;
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

  default boolean hasRealPluginId() {
    String pluginId = getPluginId();
    return pluginId != null && !pluginId.equals(PluginManagerCore.CORE_PLUGIN_ID);
  }

  @SubTag("product-descriptor")
  @NotNull ProductDescriptor getProductDescriptor();

  @SubTag("content")
  @Stubbed
  @ApiStatus.Experimental
  @NotNull ContentDescriptor getContent();

  @SubTag("dependencies")
  @Stubbed
  @ApiStatus.Experimental
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

  @NotNull GenericAttributeValue<Boolean> getImplementationDetail();

  @ApiStatus.Experimental
  @NotNull GenericAttributeValue<Boolean> getOnDemand();

  @NotNull GenericAttributeValue<Boolean> getRequireRestart();

  @ApiStatus.Experimental
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
   * @deprecated the corresponding tag in plugin.xml is not supported anymore, this method is used to highlight occurrences of such tag  
   */
  @Deprecated
  @NotNull List<? extends Helpset> getHelpsets();
}
