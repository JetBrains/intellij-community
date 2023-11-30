// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.DefaultModuleConfigurationEditorFactory;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import org.jetbrains.idea.devkit.build.PluginModuleBuildConfEditor;

import java.util.ArrayList;
import java.util.List;

final class PluginModuleEditorsProvider implements ModuleConfigurationEditorProvider {

  @Override
  public ModuleConfigurationEditor[] createEditors(ModuleConfigurationState state) {
    final Module module = state.getCurrentRootModel().getModule();
    if (ModuleType.get(module) != PluginModuleType.getInstance()) return ModuleConfigurationEditor.EMPTY;

    final DefaultModuleConfigurationEditorFactory editorFactory = DefaultModuleConfigurationEditorFactory.getInstance();
    List<ModuleConfigurationEditor> editors = new ArrayList<>();
    editors.add(editorFactory.createModuleContentRootsEditor(state));
    editors.add(editorFactory.createOutputEditor(state));
    editors.add(editorFactory.createClasspathEditor(state));
    editors.add(new PluginModuleBuildConfEditor(state));
    return editors.toArray(ModuleConfigurationEditor.EMPTY);
  }
}