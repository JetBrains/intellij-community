package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vcs.ui.Refreshable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * author: lesya
 */
public class RemoveLocallyDeletedFilesAction extends RemoveLocallyFileOrDirectoryAction {
  public RemoveLocallyDeletedFilesAction() {
    CvsActionVisibility visibility = getVisibility();
    visibility.canBePerformedOnSeveralFiles();
    visibility.canBePerformedOnLocallyDeletedFile();

    visibility.addCondition(new CvsActionVisibility.Condition() {

      public boolean isPerformedOn(CvsContext context) {
        Collection<File> fileNames = getFilesToRemove(context);
        if (fileNames.isEmpty()) return false;
        for (Iterator<File> iterator = fileNames.iterator(); iterator.hasNext();) {
          File file = iterator.next();
          if (file.isDirectory()) return false;
          if (file.isFile()) return false;
          if (!CvsUtil.fileIsUnderCvs(file)) return false;
          if (!CvsUtil.fileIsUnderCvs(file.getParentFile())) return false;
          if (CvsUtil.fileIsLocallyAdded(file)) return false;
          if (CvsUtil.fileIsLocallyRemoved(file)) return false;
        }
        return true;
      }
    });
  }

  public void actionPerformed(CvsContext context) {
    Refreshable refreshableDialog = context.getRefreshableDialog();
    if (refreshableDialog != null) {
      refreshableDialog.saveState();
    }
    super.actionPerformed(context);
  }

  protected void onActionPerformed(CvsContext context,
                                   CvsTabbedWindow tabbedWindow,
                                   boolean successfully,
                                   CvsHandler handler) {
    Refreshable refreshableDialog = context.getRefreshableDialog();
    if (refreshableDialog != null) {
      refreshableDialog.restoreState();
      refreshableDialog.refresh();
    }
    super.onActionPerformed(context, tabbedWindow, successfully, handler);
  }

  protected Collection<File> getFilesToRemove(CvsContext context) {
    File[] files = context.getSelectedIOFiles();
    ArrayList<File> result = new ArrayList<File>();
    if (files == null) return result;
    for (int i = 0; i < files.length; i++) {
      result.add(files[i]);
    }
    return result;
  }

  public void update(AnActionEvent e) {
    if (!CvsEntriesManager.getInstance().isActive()) {
      e.getPresentation().setVisible(false);
      return;
    }
    if (e.getDataContext().getData(Refreshable.PANEL) == null) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    getVisibility().applyToEvent(e);
  }
}
