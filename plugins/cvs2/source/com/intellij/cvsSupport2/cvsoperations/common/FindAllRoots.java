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

import java.io.File;
import java.util.*;

/**
 * author: lesya
 */

public class FindAllRoots {
  private final CvsEntriesManager myManager = CvsEntriesManager.getInstance();
  private int myProcessedFiels;
  private int mySuitableFiles;
  private Collection<File> myRepositories = new HashSet<File>();

  private final Collection<VirtualFile> myResult = new ArrayList<VirtualFile>();
  private final ProgressIndicator myProgress;
  private final ProjectRootManager myProjectRootManager;

  public FindAllRoots(Project project) {
    myProgress = ProgressManager.getInstance().getProgressIndicator();
    myProjectRootManager = ProjectRootManager.getInstance(project);
  }

  public Collection<VirtualFile> executeOn(final FilePath[] roots) {
    final Collection<FilePath> rootsWithoutIntersections = getRootsWithoutIntersections(roots);
    setText(com.intellij.CvsBundle.message("progress.text.searching.for.cvs.root"));
    myManager.lockSynchronizationActions();
    mySuitableFiles = calcVirtualFilesUnderCvsIn(rootsWithoutIntersections) * 2;
    myProcessedFiels = 0;
    try {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (Iterator each = rootsWithoutIntersections.iterator(); each.hasNext();) {
            FilePath file = (FilePath)each.next();
            VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile != null) {
              myResult.add(virtualFile);
              process(virtualFile);
            } else {
              myResult.add(file.getVirtualFileParent());
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
    for (Iterator iterator = rootsWithoutIntersections.iterator(); iterator.hasNext();) {
      FilePath cvsFileWrapper = (FilePath)iterator.next();
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
    if (!myProjectRootManager.getFileIndex().isInContent(file)) return 0;
    int result = 0;
    if (file == null || !file.isDirectory()) return result;
    if (file.findChild(CvsUtil.CVS) == null) return result;
    result += 1;
    VirtualFile[] children = file.getChildren();
    if (children == null) return result;
    for (int i = 0; i < children.length; i++) {
      VirtualFile child = children[i];
      if (child.getName() == CvsUtil.CVS) continue;
      result += calcVirtualFilesUnderCvsIn(child);
    }
    return result;
  }

  private Collection<FilePath> getRootsWithoutIntersections(FilePath[] roots) {
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
    myProcessedFiels++;
    setFraction();
    VirtualFile[] children = root.getChildren();
    if (children == null) return;
    CvsEnvironment parentEnv = myManager.getCvsConnectionSettingsFor(root);
    if (!parentEnv.isValid()) return;
    myManager.cacheCvsAdminInfoIn(root);
    myRepositories.add(CvsVfsUtil.getFileFor(root));
    for (int i = 0; i < children.length; i++) {
      VirtualFile child = children[i];
      if (!child.isDirectory()) continue;
      if (!myProjectRootManager.getFileIndex().isInContent(child)) continue;
      CvsEnvironment childEnv = myManager.getCvsConnectionSettingsFor(child);
      if (!childEnv.isValid()) continue;
      if (!childEnv.equals(parentEnv)) {
        myResult.add(child);
      }
      process(child);
    }
  }

  private void setFraction() {
    if (myProgress == null) return;
    myProgress.setFraction((double)myProcessedFiels / (double)mySuitableFiles);
    ProgressManager.getInstance().checkCanceled();
  }

  public Collection<File> getDirectoriesToBeUpdated() {
    return myRepositories;
  }

}
