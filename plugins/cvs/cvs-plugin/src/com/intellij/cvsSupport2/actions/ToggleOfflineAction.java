/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 04.09.2006
 * Time: 21:00:01
 */
package com.intellij.cvsSupport2.actions;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;

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
      VcsBalloonProblemNotifier.showOverChangesView(cvsContext.getProject(),
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
