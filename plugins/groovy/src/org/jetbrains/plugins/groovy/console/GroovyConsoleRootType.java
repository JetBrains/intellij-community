/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.console;

import com.intellij.execution.console.ConsoleRootType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.console.actions.GrExecuteCommandAction;
import org.jetbrains.plugins.groovy.console.actions.GrSelectModuleAction;

import javax.swing.*;

public final class GroovyConsoleRootType extends ConsoleRootType {

  public static final AnAction executeAction = new GrExecuteCommandAction();

  @NotNull
  public static GroovyConsoleRootType getInstance() {
    return findByClass(GroovyConsoleRootType.class);
  }

  public GroovyConsoleRootType() {
    super("groovy", "Groovy consoles");
  }

  @NotNull
  @Override
  public String getDefaultFileExtension() {
    return GroovyFileType.DEFAULT_EXTENSION;
  }

  @Override
  public boolean isIgnored(@NotNull Project project, @NotNull VirtualFile element) {
    return !GroovyProjectConsole.getInstance(project).isProjectConsole(element);
  }

  @Nullable
  @Override
  public String substituteName(@NotNull Project project, @NotNull VirtualFile file) {
    return file.getUserData(GroovyConsole.GROOVY_CONSOLE_TITLE);
  }

  @Override
  public void fileOpened(@NotNull final VirtualFile file, @NotNull FileEditorManager source) {
    final Project project = source.getProject();

    findConsoleModule(project, file);

    for (FileEditor fileEditor : source.getAllEditors(file)) {
      if (!(fileEditor instanceof TextEditor)) continue;
      final Editor editor = ((TextEditor)fileEditor).getEditor();
      buildEditorHeader(editor, file);
    }
  }

  private static void buildEditorHeader(Editor editor, VirtualFile file) {
    final ActionToolbar menu = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.UNKNOWN,
      new DefaultActionGroup(new GrSelectModuleAction(file)),
      true
    );

    final JPanel panel = new EditorHeaderComponent();
    panel.add(menu.getComponent());
    editor.setHeaderComponent(panel);

    executeAction.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, editor.getComponent());
  }

  @Nullable
  private static Module findConsoleModule(@NotNull Project project, @NotNull VirtualFile file) {
    final String moduleName = file.getUserData(GroovyConsole.MODULE_NAME);
    if (moduleName == null) return null;
    final Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
    file.putUserData(GroovyConsole.MODULE_NAME, null);
    file.putUserData(ModuleUtilCore.KEY_MODULE, module);
    return module;
  }
}
