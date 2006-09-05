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

import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.application.CvsEntriesManager;

public class ToggleOfflineAction extends ToggleAction {
  public boolean isSelected(AnActionEvent e) {
    CvsContext cvsContext = CvsContextWrapper.createInstance(e);
    if (!cvsContext.cvsIsActive()) return false;
    CvsConnectionSettings settings = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(cvsContext.getSelectedFile());
    if (settings == null) return false;
    return settings.isOffline();
  }

  public void setSelected(AnActionEvent e, boolean state) {
    CvsContext cvsContext = CvsContextWrapper.createInstance(e);
    CvsConnectionSettings settings = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(cvsContext.getSelectedFile());
    if (settings != null) {
      settings.setOffline(state);
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    CvsContext cvsContext = CvsContextWrapper.createInstance(e);
    e.getPresentation().setVisible(cvsContext.cvsIsActive());
  }
}
