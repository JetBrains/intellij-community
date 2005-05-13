/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.idea.devkit.module;

import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.roots.ui.configuration.DefaultModuleConfigurationEditorFactory;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.idea.devkit.build.PluginModuleBuildConfEditor;

import java.util.ArrayList;
import java.util.List;

public class PluginModuleEditorsProvider implements ModuleComponent, ModuleConfigurationEditorProvider{
  public String getComponentName() {
    return "DevKit.PluginModuleEditorsProvider";
  }


  public ModuleConfigurationEditor[] createEditors(ModuleConfigurationState state) {
    final DefaultModuleConfigurationEditorFactory editorFactory = DefaultModuleConfigurationEditorFactory.getInstance();
    ModulesProvider provider = state.getModulesProvider();
    List<ModuleConfigurationEditor> editors = new ArrayList<ModuleConfigurationEditor>();
    editors.add(editorFactory.createModuleContentRootsEditor(state));
    editors.add(editorFactory.createLibrariesEditor(state));
    if (provider.getModules().length > 1) {
      editors.add(editorFactory.createDependenciesEditor(state));
    }
    editors.add(editorFactory.createOrderEntriesEditor(state));
    editors.add(editorFactory.createJavadocEditor(state));
    editors.add(new PluginModuleBuildConfEditor(state));
    return editors.toArray(new ModuleConfigurationEditor[editors.size()]);
  }

  public void projectOpened() {}
  public void projectClosed() {}
  public void moduleAdded() {}
  public void initComponent() {}
  public void disposeComponent() {}
}