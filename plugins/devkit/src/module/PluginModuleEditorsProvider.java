/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.idea.devkit.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.DefaultModuleConfigurationEditorFactory;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.j2ee.make.ModuleBuildProperties;
import org.jetbrains.idea.devkit.build.PluginModuleBuildConfEditor;
import org.jetbrains.idea.devkit.build.PluginModuleBuildProperties;

public class PluginModuleEditorsProvider implements ModuleComponent, ModuleConfigurationEditorProvider{
  public String getComponentName() {
    return "DevKit.PluginModuleEditorsProvider";
  }


  public ModuleConfigurationEditor[] createEditors(ModuleConfigurationState state) {
    final DefaultModuleConfigurationEditorFactory editorFactory = DefaultModuleConfigurationEditorFactory.getInstance();
    final ModifiableRootModel rootModel = state.getRootModel();
    final Module module = rootModel.getModule();
    return new ModuleConfigurationEditor[] {
      editorFactory.createModuleContentRootsEditor(state),
      editorFactory.createLibrariesEditor(state),
      editorFactory.createDependenciesEditor(state),
      editorFactory.createOrderEntriesEditor(state),
      editorFactory.createJavadocEditor(state),
      new PluginModuleBuildConfEditor(state)
    };
  }

  public void projectOpened() {}
  public void projectClosed() {}
  public void moduleAdded() {}
  public void initComponent() {}
  public void disposeComponent() {}
}