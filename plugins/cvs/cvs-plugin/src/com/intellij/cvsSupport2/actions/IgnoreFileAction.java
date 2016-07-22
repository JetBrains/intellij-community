/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextAdapter;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.cvsSupport2.ui.Options;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * author: lesya
 */
public class IgnoreFileAction extends AnAction implements DumbAware {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.actions.IgnoreFileAction");

  private final CvsActionVisibility myVisibility = new CvsActionVisibility();

  public IgnoreFileAction() {
    myVisibility.canBePerformedOnSeveralFiles();
    myVisibility.addCondition(ActionOnSelectedElement.FILES_ARENT_UNDER_CVS);
    myVisibility.addCondition(ActionOnSelectedElement.FILES_ARE_NOT_IGNORED);
  }

  public void update(AnActionEvent e) {
    myVisibility.applyToEvent(e);
  }

  public void actionPerformed(AnActionEvent e) {
    final MultiMap<VirtualFile, VirtualFile> parentToSelectedChildren = MultiMap.createSmart();
    final CvsContext context = CvsContextWrapper.createCachedInstance(e);
    final VirtualFile[] selectedFiles = context.getSelectedFiles();
    for (VirtualFile selectedFile : selectedFiles) {
      final VirtualFile parent = selectedFile.getParent();
      parentToSelectedChildren.putValue(parent, selectedFile);
      try {
        CvsUtil.ignoreFile(selectedFile);
      }
      catch (IOException e1) {
        final String message = CvsBundle.message("message.error.ignore.files", selectedFile.getPresentableUrl(), e1.getLocalizedMessage());
        VcsBalloonProblemNotifier.showOverChangesView(context.getProject(), message, MessageType.ERROR);
      }
    }
    refreshFilesAndStatuses(context, parentToSelectedChildren);
  }

  private static void refreshPanel(CvsContext context) {
    final Refreshable refreshablePanel = context.getRefreshableDialog();
    if (refreshablePanel != null) {
      refreshablePanel.restoreState();
      refreshablePanel.refresh();
    }
  }

  private static void refreshFilesAndStatuses(final CvsContext context, final MultiMap<VirtualFile, VirtualFile> parentToSelectedChildren) {
    final Refreshable refreshablePanel = context.getRefreshableDialog();
    if (refreshablePanel != null) refreshablePanel.saveState();
    final int[] refreshedParents = new int[]{0};
    final Collection<VirtualFile> createdCvsIgnoreFiles = new ArrayList<>();
    for (final VirtualFile parent : parentToSelectedChildren.keySet()) {
      final Runnable runnable = new Runnable() {
        public void run() {
          try {
            final VirtualFile cvsIgnoreFile =
              CvsVfsUtil.refreshAndfFindChild(parent, CvsUtil.CVS_IGNORE_FILE);
            if (cvsIgnoreFile == null) {
              final String path = parent.getPath() + "/" + CvsUtil.CVS_IGNORE_FILE;
              LOG.error(String.valueOf(CvsVfsUtil.findFileByPath(path)) + " " + parent.getPath() + " " +
                        new File(VfsUtil.virtualToIoFile(parent), CvsUtil.CVS_IGNORE_FILE).isFile());
              return;
            }

            if (!CvsUtil.fileIsUnderCvs(cvsIgnoreFile) &&
                !ChangeListManager.getInstance(context.getProject()).isIgnoredFile(cvsIgnoreFile) &&
                !CvsEntriesManager.getInstance().fileIsIgnored(cvsIgnoreFile)) {
              createdCvsIgnoreFiles.add(cvsIgnoreFile);
            }

            final Collection<VirtualFile> filesToUpdateStatus = parentToSelectedChildren.get(parent);
            for (final VirtualFile file : filesToUpdateStatus) {
              FileStatusManager.getInstance(context.getProject()).fileStatusChanged(file);
              VcsDirtyScopeManager.getInstance(context.getProject()).fileDirty(file);
            }
          }
          finally {
            refreshedParents[0]++;
            if (refreshedParents[0] == parentToSelectedChildren.size()) {
              // all parents are refreshed
              if (createdCvsIgnoreFiles.isEmpty()) {
                refreshPanel(context);
              }
              else {
                addCvsIgnoreFilesToCvsAndRefreshPanel();
              }
            }
          }
        }

        private void addCvsIgnoreFilesToCvsAndRefreshPanel() {
          createAddFilesAction().actionPerformed(createContext(createdCvsIgnoreFiles, context));
        }

        private AddFileOrDirectoryAction createAddFilesAction() {
          return new AddFileOrDirectoryAction(CvsBundle.message("adding.cvsignore.files.to.cvs.action.name"), Options.ON_FILE_ADDING) {
            protected void onActionPerformed(CvsContext context,
                                             CvsTabbedWindow tabbedWindow,
                                             boolean successfully,
                                             CvsHandler handler) {
              refreshPanel(context);
            }
          };
        }
      };
      parent.refresh(true, true, runnable);
    }
  }

  private static CvsContextAdapter createContext(final Collection<VirtualFile> createdCvsIgnoreFiles, final CvsContext context) {
    return new CvsContextAdapter() {
      @NotNull
      public VirtualFile[] getSelectedFiles() {
        return VfsUtil.toVirtualFileArray(createdCvsIgnoreFiles);
      }

      public Refreshable getRefreshableDialog() {
        return context.getRefreshableDialog();
      }

      public Project getProject() {
        return context.getProject();
      }
    };
  }
}
