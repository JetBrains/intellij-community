// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class TestsLocationProviderUtil {
  private static final int MIN_PROXIMITY_THRESHOLD = 1;

  private TestsLocationProviderUtil() { }

  public static List<VirtualFile> findSuitableFilesFor(final String filePath, final Project project) {
    final VirtualFile file = getByFullPath(filePath);
    if (file != null) {
      return Collections.singletonList(file);
    }

    //split file by "/" in parts
    final LinkedList<String> folders = new LinkedList<>();
    final StringTokenizer st = new StringTokenizer(filePath, "/", false);
    String fileName = null;
    while (st.hasMoreTokens()) {
      final String pathComponent = st.nextToken();
      if (st.hasMoreTokens()) {
        folders.addFirst(pathComponent);
      } else {
        // last token
        fileName = pathComponent;
      }
    }
    if (fileName == null) {
      return Collections.emptyList();
    }
    final List<VirtualFile> target = findFilesClosestToTarget(folders, collectCandidates(project, fileName, true), MIN_PROXIMITY_THRESHOLD);
    return target.isEmpty() && file != null ? Collections.singletonList(file) : target;
  }

  /**
   * Looks for files with given name which are close to given path
   * @param targetParentFolders folders path
   * @param candidates
   * @param minProximityThreshold
   * @return
   */
  public static List<VirtualFile> findFilesClosestToTarget(@NotNull final List<String> targetParentFolders,
                                                           final List<? extends FileInfo> candidates,
                                                           final int minProximityThreshold) {
    // let's find all files with similar relative path

    if (candidates.isEmpty()) {
      return Collections.emptyList();
    }

    // let's iterate relative path components and determine which files are closer to our relative path
    for (String folderName : targetParentFolders) {
      for (FileInfo info : candidates) {
        info.processRelativePathComponent(folderName);
      }
    }

    // let's extract the closest files to relative path. For this we will find max proximity and  and
    // we also assume that relative files and folders should have at least one common parent folder - just
    // to remove false positives on some cases
    int maxProximity = 0;
    for (FileInfo fileInfo : candidates) {
      final int proximity = fileInfo.getProximity();
      if (proximity > maxProximity) {
        maxProximity = proximity;
      }
    }

    if (maxProximity >= minProximityThreshold) {
      final List<VirtualFile> files = new ArrayList<>();
      for (FileInfo info : candidates) {
        if (info.getProximity() == maxProximity) {
          files.add(info.getFile());
        }
      }
      return files;
    }

    return Collections.emptyList();
  }

  public static List<FileInfo> collectCandidates(final Project project, final String fileName,
                                                 final boolean includeNonProjectItems) {
    final List<FileInfo> filesInfo = new ArrayList<>();
    for (ChooseByNameContributor contributor : ChooseByNameContributor.FILE_EP_NAME.getExtensionList()) {
      // let's find files with same name in project and libraries
      final NavigationItem[] navigationItems = contributor.getItemsByName(fileName, fileName, project, includeNonProjectItems);
      for (NavigationItem navigationItem : navigationItems) {
        if (navigationItem instanceof PsiFile) {
          final VirtualFile itemFile = ((PsiFile)navigationItem).getVirtualFile();
          assert itemFile != null;

          filesInfo.add(new FileInfo(itemFile));
        }
      }
    }
    return filesInfo;
  }

  @Nullable
  private static VirtualFile getByFullPath(String filePath) {
    final VirtualFile fileByPath = LocalFileSystem.getInstance().findFileByPath(filePath);
    if (fileByPath != null) {
      return fileByPath;
    }
    // if we are in UnitTest mode probably TempFileSystem is used instead of LocalFileSystem
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return TempFileSystem.getInstance().findFileByPath(filePath);
    }
    return null;
  }

  public static class FileInfo {
    private final VirtualFile myFile;
    private VirtualFile myCurrentFolder;
    private int myProximity = 0;

    public FileInfo(VirtualFile file) {
      myFile = file;
      myCurrentFolder = myFile.getParent();
    }

    public void processRelativePathComponent(final String folderName) {
      if (myCurrentFolder == null) {
        return;
      }

      if (!folderName.equals(myCurrentFolder.getName())) {
        // if one of path components differs - no sense in checking others
        myCurrentFolder = null;
        return;
      }

      // common folder was found, let's increase proximity degree and move to parent folder
      myProximity ++;
      myCurrentFolder = myCurrentFolder.getParent();
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public int getProximity() {
      return myProximity;
    }
  }
}
