package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.CheckinProjectPanel;
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
    CheckinProjectPanel checkinPanel = getCheckinPanel(e);
    return checkinPanel == null || checkinPanel.isCheckCommitMessageSpelling();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    CheckinProjectPanel checkinPanel = getCheckinPanel(e);
    if (checkinPanel != null) {
      checkinPanel.setCheckCommitMessageSpelling(state);
    }
  }

  @Nullable
  private static CheckinProjectPanel getCheckinPanel(@Nullable AnActionEvent e) {
    if (e == null) {
      return null;
    }
    Refreshable data = Refreshable.PANEL_KEY.getData(e.getDataContext());
    if (data instanceof CheckinProjectPanel) {
      return (CheckinProjectPanel)data;
    }
    return null;
  }
}
