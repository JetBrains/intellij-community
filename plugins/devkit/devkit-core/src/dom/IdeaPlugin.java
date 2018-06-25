/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
  @Nullable
  String getPluginId();

  @NotNull
  @NameValue
  @Stubbed
  GenericDomValue<String> getId();

  @NotNull
  @SubTag("productCode")
  GenericDomValue<String> getProductCode();

  @NotNull
  @SubTag("releaseDate")
  GenericDomValue<String> getReleaseDate();

  @NotNull
  @SubTag("releaseVersion")
  GenericDomValue<Integer> getReleaseVersion();

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

  @SuppressWarnings("DeprecatedIsStillUsed")
  @NotNull
  @Deprecated
  GenericAttributeValue<Boolean> getUseIdeaClassloader();

  @NotNull
  GenericAttributeValue<Boolean> getAllowBundledUpdate();

  @NotNull
  @Stubbed
  GenericDomValue<String> getName();


  @NotNull
  GenericDomValue<String> getDescription();


  @NotNull
  GenericDomValue<String> getVersion();


  @NotNull
  Vendor getVendor();


  @NotNull
  GenericDomValue<String> getChangeNotes();


  @NotNull
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


  @NotNull
  List<Helpset> getHelpsets();

  Helpset addHelpset();
}
