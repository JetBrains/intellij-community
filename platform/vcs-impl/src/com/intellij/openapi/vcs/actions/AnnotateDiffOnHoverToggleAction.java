package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.FileAnnotation.LineModificationDetailsProvider;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AnnotateDiffOnHoverToggleAction extends ToggleAction implements DumbAware {
  private static final String KEY_ID = "SHOW_DIFF_ON_HOVER";

  @Nullable private final LineModificationDetailsProvider myProvider;

  AnnotateDiffOnHoverToggleAction(@NotNull FileAnnotation annotation) {
    super(VcsBundle.messagePointer("action.annotate.show.diff.preview.on.hover.text"));
    myProvider = annotation.getLineModificationDetailsProvider();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabledAndVisible(myProvider != null);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return isShowDiffOnHover();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    VcsUtil.setAspectAvailability(KEY_ID, state);
  }

  public static boolean isShowDiffOnHover() {
    return VcsUtil.isAspectAvailableByDefault(KEY_ID, true);
  }
}
