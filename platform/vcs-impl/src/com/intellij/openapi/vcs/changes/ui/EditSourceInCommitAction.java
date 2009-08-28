package com.intellij.openapi.vcs.changes.ui;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.pom.Navigatable;
import com.intellij.util.OpenSourceUtil;

import javax.swing.*;

public class EditSourceInCommitAction extends AnAction {
  private final DialogWrapper myDialogWrapper;

  public EditSourceInCommitAction(final DialogWrapper dialogWrapper) {
    super(ActionsBundle.actionText("EditSource"),
          ActionsBundle.actionDescription("EditSource"),
          IconLoader.getIcon("/actions/editSource.png"));
    myDialogWrapper = dialogWrapper;
  }

  public void actionPerformed(AnActionEvent e) {
    final Navigatable[] navigatableArray = e.getData(PlatformDataKeys.NAVIGATABLE_ARRAY);
    if (navigatableArray != null && navigatableArray.length > 0) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          OpenSourceUtil.navigate(navigatableArray, true);
        }
      });
      myDialogWrapper.doCancelAction();
    }
  }
}
