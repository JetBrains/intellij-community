package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.ui.Refreshable;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to toggle <code>'check commit message spelling errors'</code> processing.
 * 
 * @author Denis Zhdanov
 * @since 8/22/11 3:27 PM
 */
public class CheckCommitMessageSpellingAction extends ToggleAction implements DumbAware {

  public CheckCommitMessageSpellingAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    CommitMessageI checkinPanel = getCheckinPanel(e);
    return checkinPanel != null && checkinPanel.isCheckSpelling();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    CommitMessageI checkinPanel = getCheckinPanel(e);
    if (checkinPanel != null) {
      checkinPanel.setCheckSpelling(state);
    }
  }

  @Nullable
  private static CommitMessageI getCheckinPanel(@Nullable AnActionEvent e) {
    if (e == null) {
      return null;
    }
    Refreshable data = Refreshable.PANEL_KEY.getData(e.getDataContext());
    if (data instanceof CommitMessageI) {
      return (CommitMessageI)data;
    }
    CommitMessageI commitMessageI = VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(e.getDataContext());
    if (commitMessageI != null) {
      return commitMessageI;
    }
    return null;
  }
}
