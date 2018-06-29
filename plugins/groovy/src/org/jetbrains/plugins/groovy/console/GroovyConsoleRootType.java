// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console;

import com.intellij.execution.console.ConsoleRootType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
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
    if (!Registry.is("groovy.console.project.view.names")) return null;
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
