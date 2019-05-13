package com.intellij.openapi.wm.ex;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
@FunctionalInterface
public interface FrameEditorComponentProvider {
  ExtensionPointName<FrameEditorComponentProvider> EP = ExtensionPointName.create("com.intellij.frameEditorComponentProvider");

  JComponent createEditorComponent(@NotNull Project project);
}
