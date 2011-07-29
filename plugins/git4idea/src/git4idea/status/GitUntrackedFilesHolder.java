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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitExecutionException;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryFiles;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>
 *   Stores files which are untracked by the Git repository.
 *   Should be updated by calling {@link #add(com.intellij.openapi.vfs.VirtualFile)} and {@link #remove(com.intellij.openapi.vfs.VirtualFile)}
 *   whenever the list of unversioned files changes.
 *   Able to get the list of unversioned files from Git.
 * </p>
 * 
 * <p>
 *   This class is used by {@link git4idea.status.GitNewChangesCollector}.
 *   By keeping track of unversioned files in the Git repository we may invoke
 *   <code>'git status --porcelain --untracked-files=no'</code> which gives a significant speed boost: the command gets more than twice
 *   faster, because it doesn't need to seek for untracked files.
 * </p>
 *
 * <p>
 *   "Keeping track" means the following:
 *   <ul>
 *     <li>
 *       Once a file is created, it is added to untracked (by this class).
 *       Once a file is deleted, it is removed from untracked.
 *     </li>
 *     <li>
 *       Once a file is added to the index, it is removed from untracked.
 *       Once it is removed from the index, it is added to untracked.
 *     </li>
 *   </ul>
 *   In some cases (file creation/deletion) the file is not silently added/removed from the list - instead Git is asked for the status of
 *   this file: it is fast (since we ask only about one file), but more reliable,
 *   since the file may be created by IDEA and added to the index by IDEA independently, and events may race.
 *   <br/>
 *   Also, if .git/index changes, then a full refresh is initiated. The reason is not only untracked files tracking, but also handling
 *   committing outside IDEA, etc.
 * </p>
 * 
 * @author Kirill Likhodedov
 */
public class GitUntrackedFilesHolder implements Disposable, BulkFileListener {

  private final Project myProject;
  private final VirtualFile myRoot;
  private final ChangeListManager myChangeListManager;
  private final GitRepositoryManager myRepositoryManager;
  private final VcsDirtyScopeManager myDirtyScopeManager;
  private final GitRepositoryFiles myRepositoryFiles;

  private Set<VirtualFile> myUntrackedFiles = new HashSet<VirtualFile>();
  private final Object LOCK = new Object();

  /**
   * Creates the GitUntrackedFilesHolder for the specified root and initializes it by scanning the repository for unversioned files.
   * This is a lengthy procedure.
   */
  public static GitUntrackedFilesHolder init(@NotNull VirtualFile root, @NotNull Project project) {
    return new GitUntrackedFilesHolder(root, project);
  }
  
  private GitUntrackedFilesHolder(@NotNull VirtualFile root, @NotNull Project project) {
    myRoot = root;
    myProject = project;
    myRepositoryFiles = GitRepositoryFiles.getInstance(root);
    myChangeListManager = ChangeListManager.getInstance(project);
    myRepositoryManager = GitRepositoryManager.getInstance(project);
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(project);

    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, this);
    rescan();
  }

  @Override
  public void dispose() {
    synchronized (LOCK) {
      myUntrackedFiles.clear();
    }
  }

  /**
   * Adds the file to the list of untracked.
   */
  public void add(@NotNull VirtualFile file) {
    synchronized (LOCK) {
      myUntrackedFiles.add(file);
    }
  }

  /**
   * Adds several files to the list of untracked.
   */
  public void add(@NotNull Collection<VirtualFile> files) {
    synchronized (LOCK) {
      myUntrackedFiles.addAll(files);
    }
  }

  /**
   * Removes the file from untracked.
   */
  public void remove(@NotNull VirtualFile file) {
    synchronized (LOCK) {
      myUntrackedFiles.remove(file);
    }
  }

  /**
   * Removes several files from untracked.
   */
  public void remove(@NotNull Collection<VirtualFile> files) {
    synchronized (LOCK) {
      myUntrackedFiles.removeAll(files);
    }
  }

  /**
   * @return a copy of the set containing currently unversioned files.
   */
  @NotNull
  public Set<VirtualFile> getUntrackedFiles() {
    synchronized (LOCK) {
      return new HashSet<VirtualFile>(myUntrackedFiles);
    }
  }

  /**
   * Queries Git for unversioned files in this repository and updates the list stored by this class.
   * This is a lengthy operation.
   */
  public void rescan() {
    try {
      final Set<VirtualFile> untrackedFiles = retrieveUntrackedFiles(myProject, myRoot, null);
      synchronized (LOCK) { // collect changes, and then update at once
        myUntrackedFiles = untrackedFiles;
      }
    }
    catch (VcsException e) {
      throw new GitExecutionException("Couldn't scan for unversioned files in " + myRoot, e);
    }
  }

  /**
   * Examines the untracked status of the specified files and updates the holder if needed.
   * That is: if a file is not untracked, it should be removed from the storage, if it is untracked, it should be added there.
   * @param files files to check their untracked status.
   */
  private void rescanFiles(Set<VirtualFile> files) {
    Set<VirtualFile> untrackedFiles;
    try {
      untrackedFiles = retrieveUntrackedFiles(myProject, myRoot, files);
      files.removeAll(untrackedFiles);
      add(untrackedFiles);
      remove(files);
    }
    catch (VcsException e) {
      throw new GitExecutionException("Couldn't scan for unversioned files in " + myRoot + " among the following list:\n" + files, e);
    }
  }

  /**
   * Queries Git for the unversioned files in the given paths.
   *
   * @param files files that are to be checked for the unversioned files among them.
   *              <b>Pass <code>null</code> to query the whole repository.</b>
   * @return Unversioned files from the given scope.
   */
  @NotNull
  public static Set<VirtualFile> retrieveUntrackedFiles(@NotNull Project project, @NotNull VirtualFile root, @Nullable Collection<VirtualFile> files) throws VcsException {
    final Set<VirtualFile> untrackedFiles = new HashSet<VirtualFile>();

    if (files == null) {
      untrackedFiles.addAll(retrieveUntrackedFilesNoChunk(project, root, null));
    } else {
      for (List<String> relativePaths : VcsFileUtil.chunkFiles(root, files)) {
        untrackedFiles.addAll(retrieveUntrackedFilesNoChunk(project, root, relativePaths));
      }
    }
    
    return untrackedFiles;
  }

  @NotNull
  private static Collection<VirtualFile> retrieveUntrackedFilesNoChunk(@NotNull Project project, @NotNull VirtualFile root, @Nullable List<String> relativePaths)
    throws VcsException {
    final Set<VirtualFile> untrackedFiles = new HashSet<VirtualFile>();
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LS_FILES);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("--exclude-standard", "--others", "-z");
    h.endOptions();
    if (relativePaths != null) {
      h.addParameters(relativePaths);
    }

    final String output = h.run();
    if (StringUtil.isEmptyOrSpaces(output)) {
      return untrackedFiles;
    }

    for (String relPath : output.split("\u0000")) {
      VirtualFile f = root.findFileByRelativePath(relPath);
      assert f != null : String.format("VirtualFile shouldn't be null here. Relative path: [%s], \nFull output:\n%s",
                                              relPath, output);
      untrackedFiles.add(f);
    }
    
    return untrackedFiles;
  }

  @Override
  public void before(List<? extends VFileEvent> events) {
  }

  @Override
  public void after(List<? extends VFileEvent> events) {
    boolean indexChanged = false;
    Set<VirtualFile> filesToRefresh = new HashSet<VirtualFile>();

    for (VFileEvent event : events) {
      VirtualFile file = event.getFile();
      if (file == null || file.isDirectory()) {
        continue;
      }
      String path = file.getPath();
      if (myRepositoryFiles.isIndexFile(path)) {
        indexChanged = true;
      } else if (event instanceof VFileCreateEvent || event instanceof VFileCopyEvent ||
                 event instanceof VFileDeleteEvent || event instanceof VFileMoveEvent) {
        filesToRefresh.add(file);
      }
    }

    // if index has changed, no need to refresh specific files - we get the full status of all files
    if (indexChanged) {
      asyncRefreshStatusAndUnversionedFiles();
    } else {
      asyncRefreshFiles(filesToRefresh);
    }
  }

  private void asyncRefreshStatusAndUnversionedFiles() {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        rescan(); // update the list of unversioned files
        myDirtyScopeManager.dirDirtyRecursively(myRoot); // make ChangeListManager take status from GitNewChangesCollector + unversioned files from here.
      }
    }); 
  }

  private void asyncRefreshFiles(final Set<VirtualFile> filesCreated) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        Set<VirtualFile> filesToRefresh = new HashSet<VirtualFile>();
        for (VirtualFile file : filesCreated) {
          if (belongsToThisRepository(file) && !myChangeListManager.isIgnoredFile(file)) {
            filesToRefresh.add(file);
          }
        }
        rescanFiles(filesToRefresh);
        myDirtyScopeManager.filesDirty(filesToRefresh, null); // make ChangeListManager capture new info
      }
    });
  }

  private boolean belongsToThisRepository(VirtualFile file) {
    final GitRepository repository = myRepositoryManager.getRepositoryForFile(file);
    return repository != null && repository.getRoot().equals(myRoot);
  }
}
