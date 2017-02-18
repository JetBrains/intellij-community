/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

public class PluginModuleEditorsProvider implements ModuleConfigurationEditorProvider{

  public ModuleConfigurationEditor[] createEditors(ModuleConfigurationState state) {
    final Module module = state.getRootModel().getModule();
    if (ModuleType.get(module) != PluginModuleType.getInstance()) return ModuleConfigurationEditor.EMPTY;

    final DefaultModuleConfigurationEditorFactory editorFactory = DefaultModuleConfigurationEditorFactory.getInstance();
    List<ModuleConfigurationEditor> editors = new ArrayList<>();
    editors.add(editorFactory.createModuleContentRootsEditor(state));
    editors.add(editorFactory.createOutputEditor(state));
    editors.add(editorFactory.createClasspathEditor(state));
    editors.add(new PluginModuleBuildConfEditor(state));
    return editors.toArray(new ModuleConfigurationEditor[editors.size()]);
  }
}