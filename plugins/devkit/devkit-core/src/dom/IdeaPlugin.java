// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@DefinesXml
@Presentation(icon = "AllIcons.Nodes.Plugin", typeName = "Plugin")
@Stubbed
public interface IdeaPlugin extends DomElement {
  String TAG_NAME = "idea-plugin";

  @Nullable
  String getPluginId();

  @SubTag("product-descriptor")
  @Nullable
  ProductDescriptor getProductDescriptor();

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
  GenericDomValue<String> getResourceBundle();


  @NotNull
  @Stubbed
  @SubTagList("depends")
  List<Dependency> getDependencies();

  @SubTagList("depends")
  Dependency addDependency();

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
  @Deprecated
  @NotNull
  List<Helpset> getHelpsets();

  /**
   * @deprecated not used anymore
   */
  @Deprecated
  Helpset addHelpset();
}
