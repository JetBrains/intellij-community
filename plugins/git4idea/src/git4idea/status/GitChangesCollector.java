/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.status;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * Common
 * Serves as a container of common utility functions to collect dirty paths for both {@link GitNewChangesCollector} and
 * {@link GitOldChangesCollector}.
 *
 * @author Kirill Likhodedov
 */
abstract class GitChangesCollector {
  protected final Project myProject;
  protected final VirtualFile myVcsRoot;
  private final ChangeListManager myChangeListManager;
  private final VcsDirtyScope myDirtyScope;


  GitChangesCollector(@NotNull Project project,
                      @NotNull ChangeListManager changeListManager,
                      @NotNull VcsDirtyScope dirtyScope,
                      @NotNull VirtualFile vcsRoot) {
    myProject = project;
    myChangeListManager = changeListManager;
    myDirtyScope = dirtyScope;
    myVcsRoot = vcsRoot;
  }

  /**
   * @return the set of unversioned files (from the specified dirty scope).
   */
  abstract @NotNull Collection<VirtualFile> getUnversionedFiles();

  /**
   * @return the set of changes (changed files) from the specified dirty scope.
   */
  abstract @NotNull Collection<Change> getChanges();

  /**
   * Collect dirty file paths
   *
   * @param includeChanges if true, previous changes are included in collection
   * @return the set of dirty paths to check, the paths are automatically collapsed if the summary length more than limit
   */
  protected Collection<FilePath> dirtyPaths(boolean includeChanges) {
    final List<String> allPaths = new ArrayList<String>();

    for (FilePath p : myDirtyScope.getRecursivelyDirtyDirectories()) {
      addToPaths(p, allPaths);
    }
    for (FilePath p : myDirtyScope.getDirtyFilesNoExpand()) {
      addToPaths(p, allPaths);
    }

    if (includeChanges) {
      try {
        for (Change c : myChangeListManager.getChangesIn(myVcsRoot)) {
          switch (c.getType()) {
            case NEW:
            case DELETED:
            case MOVED:
              if (c.getAfterRevision() != null) {
                addToPaths(c.getAfterRevision().getFile(), allPaths);
              }
              if (c.getBeforeRevision() != null) {
                addToPaths(c.getBeforeRevision().getFile(), allPaths);
              }
            case MODIFICATION:
            default:
              // do nothing
          }
        }
      }
      catch (Exception t) {
        // ignore exceptions
      }
    }

    removeCommonParents(allPaths);

    final List<FilePath> paths = new ArrayList<FilePath>(allPaths.size());
    for (String p : allPaths) {
      final File file = new File(p);
      paths.add(new FilePathImpl(file, file.isDirectory()));
    }
    return paths;
  }

  protected void addToPaths(FilePath pathToAdd, List<String> paths) {
    File file = pathToAdd.getIOFile();
    if (myVcsRoot.equals(GitUtil.getGitRootOrNull(file))) {
      paths.add(file.getPath());
    }
  }

  protected static void removeCommonParents(List<String> allPaths) {
    Collections.sort(allPaths);

    String prevPath = null;
    Iterator<String> it = allPaths.iterator();
    while (it.hasNext()) {
      String path = it.next();
      if (prevPath != null && FileUtil.startsWith(path, prevPath)) {      // the file is under previous file, so enough to check the parent
        it.remove();
      }
      else {
        prevPath = path;
      }
    }
  }

}
