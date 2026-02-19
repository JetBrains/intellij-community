// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.console;

import com.intellij.execution.console.ConsoleRootType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;

public final class GroovyConsoleRootType extends ConsoleRootType {

  public static final AnAction EXECUTE_ACTION = new GrExecuteCommandAction();
  public static final String CONTENT_ID = "groovy_console";

  public static @NotNull GroovyConsoleRootType getInstance() {
    return findByClass(GroovyConsoleRootType.class);
  }

  public GroovyConsoleRootType() {
    super("groovy", GroovyBundle.message("groovy.consoles.type"));
  }

  @Override
  public @NotNull String getDefaultFileExtension() {
    return GroovyFileType.DEFAULT_EXTENSION;
  }

  @Override
  public boolean isIgnored(@NotNull Project project, @NotNull VirtualFile element) {
    return !GroovyConsoleStateService.getInstance(project).isProjectConsole(element);
  }

  @Override
  public @NotNull String getContentPathName(@NotNull String id) {
    assert id.equals(CONTENT_ID);
    return CONTENT_ID;
  }

  @Override
  public void fileOpened(final @NotNull VirtualFile file, @NotNull FileEditorManager source) {
    for (FileEditor fileEditor : source.getAllEditors(file)) {
      if (!(fileEditor instanceof TextEditor)) continue;
      EXECUTE_ACTION.registerCustomShortcutSet(CommonShortcuts.getCtrlEnter(), fileEditor.getComponent());
    }
  }
}
