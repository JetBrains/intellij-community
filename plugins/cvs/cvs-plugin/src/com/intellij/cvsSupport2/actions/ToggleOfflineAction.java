/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 04.09.2006
 * Time: 21:00:01
 */
package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.CvsBundle;

public class ToggleOfflineAction extends ToggleAction {
  public boolean isSelected(AnActionEvent e) {
    CvsContext cvsContext = CvsContextWrapper.createInstance(e);
    if (!cvsContext.cvsIsActive()) return false;
    VirtualFile root = cvsContext.getSelectedFile();
    if (root == null) return false;
    final VirtualFile firstDirInChain = root.isDirectory() ? root : root.getParent();
    if (firstDirInChain == null) return false;

    CvsConnectionSettings settings = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(firstDirInChain);
    if (settings == null) return false;
    return settings.isOffline();
  }

  public void setSelected(AnActionEvent e, boolean state) {
    CvsContext cvsContext = CvsContextWrapper.createInstance(e);
    final CvsEntriesManager entriesManager = CvsEntriesManager.getInstance();
    final VirtualFile file = cvsContext.getSelectedFile();
    if (file == null) return;
    final VirtualFile firstDirInChain = file.isDirectory() ? file : file.getParent();
    if (firstDirInChain == null) return;
    CvsConnectionSettings settings = entriesManager.getCvsConnectionSettingsFor(firstDirInChain);
    if (! settings.isValid()) {
      entriesManager.clearCachedEntriesFor(firstDirInChain);
      settings = entriesManager.getCvsConnectionSettingsFor(firstDirInChain);
    }
    if ((settings != null) && settings.isValid() && (state != settings.isOffline())) {
      ChangesViewBalloonProblemNotifier.showMe(cvsContext.getProject(),
                                               state ? CvsBundle.message("set.offline.notification.text") :
                                                       CvsBundle.message("set.online.notification.text"),
                                               state ? MessageType.WARNING : MessageType.INFO);
      settings.setOffline(state);
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    CvsContext cvsContext = CvsContextWrapper.createInstance(e);
    final VirtualFile[] files = cvsContext.getSelectedFiles();
    e.getPresentation().setVisible(files != null && files.length > 0 && cvsContext.cvsIsActive());
  }
}
