package com.intellij.cvsSupport2.checkinProject;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.RestoreFileAction;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextAdapter;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

/**
 * author: lesya
 */
public class CvsRollbacker {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.checkinProject.CvsRollbacker");

  private final Project myProject;

  public CvsRollbacker(Project project) {
    myProject = project;
  }

  public boolean rollbackFileModifying(VirtualFile parent, String name) {
    return restoreFile(parent, name);
  }

  public boolean rollbackFileDeleting(VirtualFile parent, String name) {
    return restoreFile(parent, name);
  }

  public static boolean rollbackFileCreating(VirtualFile parent, String name) throws IOException {
    CvsUtil.removeEntryFor(CvsVfsUtil.getFileFor(parent, name));
    return true;
  }

  private boolean restoreFile(final VirtualFile parent, String name) {
    try {
      new RestoreFileAction(parent, name).actionPerformed(createDataContext());
    }
    catch (Exception e) {
      LOG.error(e);
      return false;
    }
    return true;
  }

  private CvsContext createDataContext() {
    return new CvsContextAdapter() {
      public Project getProject() {
        return myProject;
      }
    };
  }
}
