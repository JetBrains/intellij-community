package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextAdapter;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.cvsSupport2.ui.Options;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * author: lesya
 */

public class IgnoreFileAction extends AnAction {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.actions.IgnoreFileAction");

  private final CvsActionVisibility myVisibility = new CvsActionVisibility();
  private final Map<VirtualFile, Set> myParentToSelectedChildren = new com.intellij.util.containers.HashMap<VirtualFile, Set>();

  public IgnoreFileAction() {
    myVisibility.canBePerformedOnSeveralFiles();
    myVisibility.addCondition(ActionOnSelectedElement.FILES_HAVE_PARENT_UNDER_CVS);
    myVisibility.addCondition(ActionOnSelectedElement.FILES_ARENT_UNDER_CVS);
    myVisibility.addCondition(ActionOnSelectedElement.FILES_ARE_NOT_IGNORED);
  }


  public void update(AnActionEvent e) {
    myVisibility.applyToEvent(e);
  }

  public void actionPerformed(AnActionEvent e) {
    CvsContext context = CvsContextWrapper.on(e);
    VirtualFile[] selectedFiles = context.getSelectedFiles();

    for (int i = 0; i < selectedFiles.length; i++) {
      VirtualFile selectedFile = selectedFiles[i];
      VirtualFile parent = selectedFile.getParent();
      if (!myParentToSelectedChildren.containsKey(parent)) myParentToSelectedChildren.put(parent, new HashSet());
      myParentToSelectedChildren.get(parent).add(selectedFile);
      try {
        CvsUtil.ignoreFile(selectedFile);
      }
      catch (IOException e1) {
        Messages.showErrorDialog("Cannot ignore file " + selectedFile.getPresentableUrl() + ": " + e1.getLocalizedMessage(),
                                 "Ignore Files");
      }
    }

    refreshFilesAndStatuses(context);

  }

  private void refreshPanel(CvsContext context) {
    Refreshable refreshablePanel = context.getRefreshableDialog();
    if (refreshablePanel != null) {
      refreshablePanel.restoreState();
      refreshablePanel.refresh();
    }
  }

  private void refreshFilesAndStatuses(final CvsContext context) {
    Refreshable refreshablePanel = context.getRefreshableDialog();
    if (refreshablePanel != null) refreshablePanel.saveState();
    final int refreshedParents[] = new int[]{0};
    final Collection<VirtualFile> createdCvsIgnoreFiles = new ArrayList<VirtualFile>();
    for (Iterator<VirtualFile> each = myParentToSelectedChildren.keySet().iterator(); each.hasNext();) {
      final VirtualFile parent = each.next();
      parent.refresh(true, true, parentPostRefreshAction(refreshedParents, createdCvsIgnoreFiles, context, parent));
    }
  }

  private Runnable parentPostRefreshAction(final int[] refreshedParents,
                                           final Collection<VirtualFile> createdCvsIgnoreFiles,
                                           final CvsContext context,
                                           final VirtualFile parent) {
    return new Runnable() {
      public void run() {
        try {
          VirtualFile cvsIgnoreFile = CvsVfsUtil.refreshAndfFindChild(parent, CvsUtil.CVS_IGNORE_FILE);
          if (cvsIgnoreFile == null) {
            String path = parent.getPath() + "/" + CvsUtil.CVS_IGNORE_FILE;
            LOG.assertTrue(false,
                           String.valueOf(CvsVfsUtil.findFileByPath(path)) + " " + parent.getPath() + " " +
                           new File(VfsUtil.virtualToIoFile(parent), CvsUtil.CVS_IGNORE_FILE).isFile());
            return;
          }

          if (!CvsUtil.fileIsUnderCvs(cvsIgnoreFile)) {
            createdCvsIgnoreFiles.add(cvsIgnoreFile);
          }

          Set filesToUpdateStatus = myParentToSelectedChildren.get(parent);
          for (Iterator iterator = filesToUpdateStatus.iterator(); iterator.hasNext();) {
            FileStatusManager.getInstance(context.getProject()).fileStatusChanged((VirtualFile)iterator.next());
          }
        }
        finally {
          refreshedParents[0]++;
          if (allParentsWasRefreshed(refreshedParents)) {
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
        return new AddFileOrDirectoryAction("Adding .cvsignore Files to CVS", Options.ON_FILE_ADDING, true) {
          protected void onActionPerformed(CvsContext context,
                                           CvsTabbedWindow tabbedWindow,
                                           boolean successfully,
                                           CvsHandler handler) {
            refreshPanel(context);
          }
        };
      }
    };
  }

  private CvsContextAdapter createContext(final Collection<VirtualFile> createdCvsIgnoreFiles,
                                          final CvsContext context) {
    return new CvsContextAdapter() {
      public VirtualFile[] getSelectedFiles() {
        return createdCvsIgnoreFiles.toArray(new VirtualFile[createdCvsIgnoreFiles.size()]);
      }

      public Refreshable getRefreshableDialog() {
        return context.getRefreshableDialog();
      }

      public Project getProject() {
        return context.getProject();
      }
    };
  }

  private boolean allParentsWasRefreshed(final int[] refreshedParents) {
    return refreshedParents[0] == myParentToSelectedChildren.size();
  }
}
