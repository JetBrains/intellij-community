// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable @NlsSafe
  String getPluginId();

  default boolean hasRealPluginId() {
    String pluginId = getPluginId();
    return pluginId != null && !pluginId.equals(PluginManagerCore.CORE_PLUGIN_ID);
  }

  @SubTag("product-descriptor")
  @NotNull
  ProductDescriptor getProductDescriptor();

  @SubTag("content")
  @NotNull
  @Stubbed
  @ApiStatus.Experimental
  ContentDescriptor getContent();

  @SubTag("dependencies")
  @NotNull
  @Stubbed
  @ApiStatus.Experimental
  DependencyDescriptor getDependencies();

  @NotNull
  @NameValue
  @Stubbed
  GenericDomValue<String> getId();

  /**
   * @deprecated Unused.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @NotNull
  @Attribute("version")
  @Deprecated
  GenericAttributeValue<Integer> getIdeaPluginVersion();

  @NotNull
  GenericAttributeValue<String> getUrl();

  /**
   * @deprecated Unused.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @NotNull
  @Deprecated
  GenericAttributeValue<Boolean> getUseIdeaClassloader();

  @NotNull
  GenericAttributeValue<Boolean> getAllowBundledUpdate();

  @NotNull
  GenericAttributeValue<Boolean> getImplementationDetail();

  @NotNull
  GenericAttributeValue<Boolean> getRequireRestart();

  @NotNull
  @Stubbed
  @Convert(IdeaPluginPackageConverter.class)
  GenericAttributeValue<PsiPackage> getPackage();

  @NotNull
  @Stubbed
  @Required(false)
  GenericDomValue<String> getName();


  @NotNull
  GenericDomValue<String> getDescription();


  @NotNull
  @Required(false)
  GenericDomValue<String> getVersion();


  @NotNull
  Vendor getVendor();


  @NotNull
  GenericDomValue<String> getChangeNotes();


  @NotNull
  @Stubbed
  IdeaVersion getIdeaVersion();


  @NotNull
  GenericDomValue<String> getCategory();


  @NotNull
  @Stubbed
  GenericDomValue<String> getResourceBundle();


  @NotNull
  @Stubbed
  @SubTagList("depends")
  List<Dependency> getDepends();

  @SubTagList("depends")
  Dependency addDependency();

  @NotNull
  @SubTagList("incompatible-with")
  List<GenericDomValue<String>> getIncompatibilities();

  @NotNull
  @Stubbed
  @SubTagList("module")
  List<PluginModule> getModules();

  @NotNull
  @SubTagList("extensions")
  @Stubbed
  List<Extensions> getExtensions();

  Extensions addExtensions();

  @NotNull
  @Stubbed
  @SubTagList("extensionPoints")
  List<ExtensionPoints> getExtensionPoints();

  ExtensionPoints addExtensionPoints();


  @NotNull
  @SubTagList("application-components")
  List<ApplicationComponents> getApplicationComponents();

  ApplicationComponents addApplicationComponents();

  @NotNull
  @SubTagList("project-components")
  List<ProjectComponents> getProjectComponents();

  ProjectComponents addProjectComponents();

  @NotNull
  @SubTagList("module-components")
  List<ModuleComponents> getModuleComponents();

  ModuleComponents addModuleComponents();

  @NotNull
  @SubTagList("actions")
  @Stubbed
  List<Actions> getActions();

  Actions addActions();

  /**
   * Available since 192.
   */
  @NotNull
  @SubTagList("applicationListeners")
  List<Listeners> getApplicationListeners();

  /**
   * Available since 192.
   */
  @NotNull
  @SubTagList("projectListeners")
  List<Listeners> getProjectListeners();

  /**
   * @deprecated not used anymore
   */
  @SuppressWarnings("SpellCheckingInspection")
  @Deprecated(forRemoval = true)
  @NotNull
  List<Helpset> getHelpsets();
}
