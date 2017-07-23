/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.console.actions.GrExecuteCommandAction;

public final class GroovyConsoleRootType extends ConsoleRootType {

  public static final AnAction EXECUTE_ACTION = new GrExecuteCommandAction();
  public static final String CONTENT_ID = "groovy_console";

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
    return !GroovyConsoleStateService.getInstance(project).isProjectConsole(element);
  }

  @NotNull
  @Override
  public String getContentPathName(@NotNull String id) {
    assert id == CONTENT_ID;
    return CONTENT_ID;
  }

  @Nullable
  @Override
  public String substituteName(@NotNull Project project, @NotNull VirtualFile file) {
    final String name = file.getName();
    final String moduleTitle = GroovyConsoleStateService.getInstance(project).getSelectedModuleTitle(file);
    return name.startsWith(CONTENT_ID)
           ? StringUtil.replace(name, CONTENT_ID, moduleTitle == null ? "unknown" : moduleTitle)
           : String.format("%s-%s", moduleTitle, name);
  }

  @Override
  public void fileOpened(@NotNull final VirtualFile file, @NotNull FileEditorManager source) {
    for (FileEditor fileEditor : source.getAllEditors(file)) {
      if (!(fileEditor instanceof TextEditor)) continue;
      EXECUTE_ACTION.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, fileEditor.getComponent());
    }
  }
}
