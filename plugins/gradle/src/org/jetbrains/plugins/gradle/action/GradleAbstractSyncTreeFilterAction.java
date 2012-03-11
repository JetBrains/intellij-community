package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 3/7/12 3:48 PM
 */
public abstract class GradleAbstractSyncTreeFilterAction extends ToggleAction {
  
  @NotNull private final AttributesDescriptor myDescriptor;

  protected GradleAbstractSyncTreeFilterAction(@NotNull AttributesDescriptor descriptor) {
    myDescriptor = descriptor;
    getTemplatePresentation().setText(descriptor.getDisplayName());
    final Color color = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(descriptor.getKey()).getForegroundColor();
    getTemplatePresentation().setIcon(new ColorIcon(new JLabel("").getFont().getSize(), color));
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    // TODO den implement
    return false;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    // TODO den implement
    System.out.println("GradleAbstractSyncTreeFilterAction.setSelected(): " + state);
  }
}
