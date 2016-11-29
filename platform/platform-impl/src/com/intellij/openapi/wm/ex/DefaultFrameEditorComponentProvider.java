package com.intellij.openapi.wm.ex;

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class DefaultFrameEditorComponentProvider implements FrameEditorComponentProvider {
  @Override
  public JComponent createEditorComponent(@NotNull Project project) {
    FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(project);
    return editorManager.getComponent();
  }
}
