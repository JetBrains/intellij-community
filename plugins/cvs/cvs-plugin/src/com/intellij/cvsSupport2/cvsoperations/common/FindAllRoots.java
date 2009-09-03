package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.CvsBundle;

import java.io.File;
import java.util.*;

/**
 * author: lesya
 */

public class FindAllRoots {
  private final CvsEntriesManager myManager = CvsEntriesManager.getInstance();
  private int myProcessedFiles;
  private int mySuitableFiles;
  private final Collection<File> myRepositories = new HashSet<File>();

  private final Collection<VirtualFile> myResult = new ArrayList<VirtualFile>();
  private final ProgressIndicator myProgress;
  private final ProjectRootManager myProjectRootManager;

  public FindAllRoots(Project project) {
    myProgress = ProgressManager.getInstance().getProgressIndicator();
    myProjectRootManager = ProjectRootManager.getInstance(project);
  }

  public Collection<VirtualFile> executeOn(final FilePath[] roots) {
    final Collection<FilePath> rootsWithoutIntersections = getRootsWithoutIntersections(roots);
    setText(CvsBundle.message("progress.text.searching.for.cvs.root"));
    myManager.lockSynchronizationActions();
    mySuitableFiles = calcVirtualFilesUnderCvsIn(rootsWithoutIntersections) * 2;
    myProcessedFiles = 0;
    try {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (final FilePath file : rootsWithoutIntersections) {
            VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile != null) {
              myResult.add(virtualFile);
              process(virtualFile);
            }
            else {
              VirtualFile virtualFileParent = file.getVirtualFileParent();
              if (virtualFileParent != null) {
                myResult.add(virtualFileParent);
              }
            }
          }
        }
      });
    }
    finally {
      myManager.unlockSynchronizationActions();
    }

    return myResult;
  }

  private int calcVirtualFilesUnderCvsIn(Collection<FilePath> rootsWithoutIntersections) {
    int result = 0;
    for (final FilePath cvsFileWrapper : rootsWithoutIntersections) {
      if (!cvsFileWrapper.isDirectory()) {
        result += 1;
      }
      else {
        result += calcVirtualFilesUnderCvsIn(cvsFileWrapper.getVirtualFile());
      }
    }
    return result;
  }

  private int calcVirtualFilesUnderCvsIn(VirtualFile file) {
    if (file == null || !file.isDirectory()) return 0;
    if (!myProjectRootManager.getFileIndex().isInContent(file)) return 0;
    int result = 0;
    if (file.findChild(CvsUtil.CVS) == null) return result;
    result += 1;
    VirtualFile[] children = file.getChildren();
    if (children == null) return result;
    for (VirtualFile child : children) {
      if (child.getName() == CvsUtil.CVS) continue;
      result += calcVirtualFilesUnderCvsIn(child);
    }
    return result;
  }

  private static Collection<FilePath> getRootsWithoutIntersections(FilePath[] roots) {
    ArrayList<FilePath> result = new ArrayList<FilePath>();
    List<FilePath> list = Arrays.asList(roots);
    Collections.sort(list, new Comparator<FilePath>() {
      public int compare(FilePath file, FilePath file1) {
        return file.getPath().compareTo(file1.getPath());
      }
    });
    FilePath[] sortedRoots = list.toArray(new FilePath[list.size()]);
    for (int i = 0; i < sortedRoots.length; i++) {
      FilePath root = sortedRoots[i];
      if (i == 0) {
        result.add(root);
      }
      else {
        FilePath prevRoot = result.isEmpty() ? null : result.get(result.size() - 1);
        if ((prevRoot == null) || ! VfsUtil.isAncestor(prevRoot.getIOFile(), root.getIOFile(), false)){
          result.add(root);
        }
      }
    }

    return result;
  }

  private void setText(final String text) {
    if (myProgress == null) return;
    myProgress.setText(text);
    ProgressManager.getInstance().checkCanceled();
  }

  private void setText2(final String text) {
    if (myProgress == null) return;
    myProgress.setText2(text);
    ProgressManager.getInstance().checkCanceled();
  }

  private void process(VirtualFile root) {
    if (!myProjectRootManager.getFileIndex().isInContent(root)) return;
    setText2(CvsVfsUtil.getPresentablePathFor(root));
    myProcessedFiles++;
    setFraction();
    VirtualFile[] children = root.getChildren();
    if (children == null) return;
    CvsEnvironment parentEnv = myManager.getCvsConnectionSettingsFor(root);
    if (!parentEnv.isValid()) return;
    myManager.cacheCvsAdminInfoIn(root);
    myRepositories.add(CvsVfsUtil.getFileFor(root));
    for (VirtualFile child : children) {
      if (!child.isDirectory()) continue;
      if (!myProjectRootManager.getFileIndex().isInContent(child)) continue;
      CvsEnvironment childEnv = myManager.getCvsConnectionSettingsFor(child);
      if (childEnv == null || !childEnv.isValid()) continue;
      if (!childEnv.equals(parentEnv)) {
        myResult.add(child);
      }
      process(child);
    }
  }

  private void setFraction() {
    if (myProgress == null) return;
    myProgress.setFraction((double)myProcessedFiles / (double)mySuitableFiles);
    ProgressManager.getInstance().checkCanceled();
  }

  public Collection<File> getDirectoriesToBeUpdated() {
    return myRepositories;
  }

}
