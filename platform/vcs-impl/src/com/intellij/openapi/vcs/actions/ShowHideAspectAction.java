// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class ShowHideAspectAction extends ToggleAction implements DumbAware {
  private final AnnotationFieldGutter myGutter;
  private boolean isAvailable = true;

  public ShowHideAspectAction(AnnotationFieldGutter gutter) {
    super(gutter.getDisplayName());
    myGutter = gutter;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return isSelected();
  }

  boolean isSelected() {
    return myGutter.isAvailable();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    VcsUtil.setAspectAvailability(myGutter.getID(), state);

    AnnotateActionGroup.revalidateMarkupInAllEditors();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(isAvailable);
  }

  public void setAvailable(boolean available) {
    isAvailable = available;
  }
}
