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
package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

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
        for (File file : fileNames) {
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
    ContainerUtil.addAll(result, files);
    return result;
  }

  public void update(AnActionEvent e) {
    if (!CvsEntriesManager.getInstance().isActive()) {
      e.getPresentation().setVisible(false);
      return;
    }
    if (Refreshable.PANEL_KEY.getData(e.getDataContext()) == null) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    getVisibility().applyToEvent(e);
  }
}
