package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.containers.Convertor;

/**
 * @author yole
 */
public class RevertChangeListAction extends RevertCommittedStuffAbstractAction {
  public RevertChangeListAction() {
    super(new Convertor<AnActionEvent, Change[]>() {
      public Change[] convert(AnActionEvent e) {
        return e.getData(VcsDataKeys.CHANGES);
      }
    }, new Convertor<AnActionEvent, Change[]>() {
      public Change[] convert(AnActionEvent e) {
        return e.getData(VcsDataKeys.CHANGES_WITH_MOVED_CHILDREN);
      }
    });
  }
}
