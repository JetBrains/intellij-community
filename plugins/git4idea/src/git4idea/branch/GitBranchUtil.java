/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.branch;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.*;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVcsSettings;
import git4idea.repo.*;
import git4idea.ui.branch.GitMultiRootBranchConfig;
import git4idea.validators.GitNewBranchNameValidator;
import org.intellij.images.editor.ImageFileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class GitBranchUtil {

  private static final Logger LOG = Logger.getInstance(GitBranchUtil.class);

  private static final Function<GitBranch,String> BRANCH_TO_NAME = new Function<GitBranch, String>() {
    @Override
    public String apply(@Nullable GitBranch input) {
      assert input != null;
      return input.getName();
    }
  };

  private GitBranchUtil() {}

  /**
   * Returns the tracking information about the given branch in the given repository,
   * or null if there is no such information (i.e. if the branch doesn't have a tracking branch).
   */
  @Nullable
  public static GitBranchTrackInfo getTrackInfoForBranch(@NotNull GitRepository repository, @NotNull GitLocalBranch branch) {
    for (GitBranchTrackInfo trackInfo : repository.getBranchTrackInfos()) {
      if (trackInfo.getLocalBranch().equals(branch)) {
        return trackInfo;
      }
    }
    return null;
  }

  @NotNull
  static String getCurrentBranchOrRev(@NotNull Collection<GitRepository> repositories) {
    if (repositories.size() > 1) {
      GitMultiRootBranchConfig multiRootBranchConfig = new GitMultiRootBranchConfig(repositories);
      String currentBranch = multiRootBranchConfig.getCurrentBranch();
      LOG.assertTrue(currentBranch != null, "Repositories have unexpectedly diverged. " + multiRootBranchConfig);
      return currentBranch;
    }
    else {
      assert !repositories.isEmpty() : "No repositories passed to GitBranchOperationsProcessor.";
      GitRepository repository = repositories.iterator().next();
      return getBranchNameOrRev(repository);
    }
  }

  @NotNull
  public static Collection<String> convertBranchesToNames(@NotNull Collection<? extends GitBranch> branches) {
    return Collections2.transform(branches, BRANCH_TO_NAME);
  }

  /**
   * Returns the current branch in the given repository, or null if either repository is not on the branch, or in case of error.
   * @deprecated Use {@link GitRepository#getCurrentBranch()}
   */
  @Deprecated
  @Nullable
  public static GitLocalBranch getCurrentBranch(@NotNull Project project, @NotNull VirtualFile root) {
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root);
    if (repository != null) {
      return repository.getCurrentBranch();
    }
    else {
      LOG.info("getCurrentBranch: Repository is null for root " + root);
      return getCurrentBranchFromGit(project, root);
    }
  }

  @Nullable
  private static GitLocalBranch getCurrentBranchFromGit(@NotNull Project project, @NotNull VirtualFile root) {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.REV_PARSE);
    handler.addParameters("--abbrev-ref", "HEAD");
    handler.setSilent(true);
    try {
      String name = handler.run();
      if (!name.equals("HEAD")) {
        return new GitLocalBranch(name, GitBranch.DUMMY_HASH);
      }
      else {
        return null;
      }
    }
    catch (VcsException e) {
      LOG.info("git rev-parse --abbrev-ref HEAD", e);
      return null;
    }
  }

  /**
   * Get tracked remote for the branch
   */
  @Nullable
  public static String getTrackedRemoteName(Project project, VirtualFile root, String branchName) throws VcsException {
    return GitConfigUtil.getValue(project, root, trackedRemoteKey(branchName));
  }

  /**
   * Get tracked branch of the given branch
   */
  @Nullable
  public static String getTrackedBranchName(Project project, VirtualFile root, String branchName) throws VcsException {
    return GitConfigUtil.getValue(project, root, trackedBranchKey(branchName));
  }

  @NotNull
  private static String trackedBranchKey(String branchName) {
    return "branch." + branchName + ".merge";
  }

  @NotNull
  private static String trackedRemoteKey(String branchName) {
    return "branch." + branchName + ".remote";
  }

  /**
   * Get the tracking branch for the given branch, or null if the given branch doesn't track anything.
   * @deprecated Use {@link GitConfig#getBranchTrackInfos()}
   */
  @Deprecated
  @Nullable
  public static GitRemoteBranch tracked(@NotNull Project project, @NotNull VirtualFile root, @NotNull String branchName) throws VcsException {
    final HashMap<String, String> result = new HashMap<String, String>();
    GitConfigUtil.getValues(project, root, null, result);
    String remoteName = result.get(trackedRemoteKey(branchName));
    if (remoteName == null) {
      return null;
    }
    String branch = result.get(trackedBranchKey(branchName));
    if (branch == null) {
      return null;
    }

    if (".".equals(remoteName)) {
      return new GitSvnRemoteBranch(branch, GitBranch.DUMMY_HASH);
    }

    GitRemote remote = findRemoteByNameOrLogError(project, root, remoteName);
    if (remote == null) return null;
    return new GitStandardRemoteBranch(remote, branch, GitBranch.DUMMY_HASH);
  }

  @Nullable
  @Deprecated
  public static GitRemote findRemoteByNameOrLogError(@NotNull Project project, @NotNull VirtualFile root, @NotNull String remoteName) {
    GitRepository repository = GitUtil.getRepositoryForRootOrLogError(project, root);
    if (repository == null) {
      return null;
    }

    GitRemote remote = GitUtil.findRemoteByName(repository, remoteName);
    if (remote == null) {
      LOG.warn("Couldn't find remote with name " + remoteName);
      return null;
    }
    return remote;
  }

  /**
   *
   * @return {@link git4idea.GitStandardRemoteBranch} or {@link GitSvnRemoteBranch}, or null in case of an error. The error is logged in this method.
   * @deprecated Should be used only in the GitRepositoryReader, i. e. moved there once all other usages are removed.
   */
  @Deprecated
  @Nullable
  public static GitRemoteBranch parseRemoteBranch(@NotNull String fullBranchName, @NotNull Hash hash,
                                                  @NotNull Collection<GitRemote> remotes) {
    String stdName = stripRefsPrefix(fullBranchName);

    int slash = stdName.indexOf('/');
    if (slash == -1) { // .git/refs/remotes/my_branch => git-svn
      return new GitSvnRemoteBranch(fullBranchName, hash);
    }
    else {
      String remoteName = stdName.substring(0, slash);
      String branchName = stdName.substring(slash + 1);
      GitRemote remote = findRemoteByName(remoteName, remotes);
      if (remote == null) {
        return null;
      }
      return new GitStandardRemoteBranch(remote, branchName, hash);
    }
  }

  @Nullable
  private static GitRemote findRemoteByName(@NotNull String remoteName, @NotNull Collection<GitRemote> remotes) {
    for (GitRemote remote : remotes) {
      if (remote.getName().equals(remoteName)) {
        return remote;
      }
    }
    // user may remove the remote section from .git/config, but leave remote refs untouched in .git/refs/remotes
    LOG.info(String.format("No remote found with the name [%s]. All remotes: %s", remoteName, remotes));
    return null;
  }

  /**
   * Convert {@link git4idea.GitRemoteBranch GitRemoteBranches} to their names, and remove remote HEAD pointers: origin/HEAD.
   */
  @NotNull
  public static Collection<String> getBranchNamesWithoutRemoteHead(@NotNull Collection<GitRemoteBranch> remoteBranches) {
    return Collections2.filter(convertBranchesToNames(remoteBranches), new Predicate<String>() {
      @Override
      public boolean apply(@Nullable String input) {
        assert input != null;
        return !input.equals("HEAD");
      }
    });
  }

  /**
   * @deprecated Don't use names, use {@link GitLocalBranch} objects.
   */
  @Deprecated
  @Nullable
  public static GitLocalBranch findLocalBranchByName(@NotNull GitRepository repository, @NotNull final String branchName) {
    Optional<GitLocalBranch> optional = Iterables.tryFind(repository.getBranches().getLocalBranches(), new Predicate<GitLocalBranch>() {
      @Override
      public boolean apply(@Nullable GitLocalBranch input) {
        assert input != null;
        return input.getName().equals(branchName);
      }
    });
    if (optional.isPresent()) {
      return optional.get();
    }
    LOG.info(String.format("Couldn't find branch with name %s in %s", branchName, repository));
    return null;

  }

  /**
   * Looks through the remote branches in the given repository and tries to find the one from the given remote,
   * which the given name.
   * @return remote branch or null if such branch couldn't be found.
   */
  @Nullable
  public static GitRemoteBranch findRemoteBranchByName(@NotNull String remoteBranchName, @NotNull final String remoteName,
                                                       @NotNull final Collection<GitRemoteBranch> remoteBranches) {
    final String branchName = stripRefsPrefix(remoteBranchName);
    Optional<GitRemoteBranch> optional = Iterables.tryFind(remoteBranches, new Predicate<GitRemoteBranch>() {
      @Override
      public boolean apply(@Nullable GitRemoteBranch input) {
        assert input != null;
        return input.getNameForRemoteOperations().equals(branchName) && input.getRemote().getName().equals(remoteName);
      }
    });
    if (optional.isPresent()) {
      return optional.get();
    }
    LOG.info(String.format("Couldn't find branch with name %s", branchName));
    return null;
  }

  @NotNull
  public static String stripRefsPrefix(@NotNull String branchName) {
    if (branchName.startsWith(GitBranch.REFS_HEADS_PREFIX)) {
      return branchName.substring(GitBranch.REFS_HEADS_PREFIX.length());
    }
    else if (branchName.startsWith(GitBranch.REFS_REMOTES_PREFIX)) {
      return branchName.substring(GitBranch.REFS_REMOTES_PREFIX.length());
    }
    return branchName;
  }

  /**
   * Returns current branch name (if on branch) or current revision otherwise.
   * For fresh repository returns an empty string.
   */
  @NotNull
  public static String getBranchNameOrRev(@NotNull GitRepository repository) {
    if (repository.isOnBranch()) {
      GitBranch currentBranch = repository.getCurrentBranch();
      assert currentBranch != null;
      return currentBranch.getName();
    } else {
      String currentRevision = repository.getCurrentRevision();
      return currentRevision != null ? currentRevision.substring(0, 7) : "";
    }
  }

  /**
   * Shows a message dialog to enter the name of new branch.
   * @return name of new branch or {@code null} if user has cancelled the dialog.
   */
  @Nullable
  public static String getNewBranchNameFromUser(@NotNull Project project, @NotNull Collection<GitRepository> repositories, @NotNull String dialogTitle) {
    return Messages.showInputDialog(project, "Enter the name of new branch:", dialogTitle, Messages.getQuestionIcon(), "",
                                    GitNewBranchNameValidator.newInstance(repositories));
  }

  /**
   * Returns the text that is displaying current branch.
   * In the simple case it is just the branch name, but in detached HEAD state it displays the hash or "rebasing master".
   */
  public static String getDisplayableBranchText(@NotNull GitRepository repository) {
    GitRepository.State state = repository.getState();
    if (state == GitRepository.State.DETACHED) {
      String currentRevision = repository.getCurrentRevision();
      assert currentRevision != null : "Current revision can't be null in DETACHED state, only on the fresh repository.";
      return currentRevision.substring(0, 7);
    }

    String prefix = "";
    if (state == GitRepository.State.MERGING || state == GitRepository.State.REBASING) {
      prefix = state.toString() + " ";
    }

    GitBranch branch = repository.getCurrentBranch();
    String branchName = (branch == null ? "" : branch.getName());
    return prefix + branchName;
  }

  /**
   * Returns the currently selected file, based on which GitBranch components ({@link git4idea.ui.branch.GitBranchPopup}, {@link git4idea.ui.branch.GitBranchWidget})
   * will identify the current repository root.
   */
  @Nullable
  static VirtualFile getSelectedFile(@NotNull Project project) {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    final FileEditor fileEditor = StatusBarUtil.getCurrentFileEditor(project, statusBar);
    VirtualFile result = null;
    if (fileEditor != null) {
      if (fileEditor instanceof TextEditor) {
        Document document = ((TextEditor)fileEditor).getEditor().getDocument();
        result = FileDocumentManager.getInstance().getFile(document);
      } else if (fileEditor instanceof ImageFileEditor) {
        result = ((ImageFileEditor)fileEditor).getImageEditor().getFile();
      }
    }

    if (result == null) {
      final FileEditorManager manager = FileEditorManager.getInstance(project);
      if (manager != null) {
        Editor editor = manager.getSelectedTextEditor();
        if (editor != null) {
          result = FileDocumentManager.getInstance().getFile(editor.getDocument());
        }
      }
    }
    return result;
  }

  /**
   * Guesses the Git root on which a Git action is to be invoked.
   * <ol>
   *   <li>
   *     Returns the root for the selected file. Selected file is determined by {@link #getSelectedFile(com.intellij.openapi.project.Project)}.
   *     If selected file is unknown (for example, no file is selected in the Project View or Changes View and no file is open in the editor),
   *     continues guessing. Otherwise returns the Git root for the selected file. If the file is not under a known Git root,
   *     <code>null</code> will be returned - the file is definitely determined, but it is not under Git.
   *   </li>
   *   <li>
   *     Takes all Git roots registered in the Project. If there is only one, it is returned.
   *   </li>
   *   <li>
   *     If there are several Git roots,
   *   </li>
   * </ol>
   *
   * <p>
   *   NB: This method has to be accessed from the <b>read action</b>, because it may query
   *   {@link com.intellij.openapi.fileEditor.FileEditorManager#getSelectedTextEditor()}.
   * </p>
   * @param project current project
   * @return Git root that may be considered as "current".
   *         <code>null</code> is returned if a file not under Git was explicitly selected, if there are no Git roots in the project,
   *         or if the current Git root couldn't be determined.
   */
  @Nullable
  public static GitRepository getCurrentRepository(@NotNull Project project) {
    GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
    VirtualFile file = getSelectedFile(project);
    VirtualFile root = getVcsRootOrGuess(project, file);
    return manager.getRepositoryForRoot(root);
  }

  @Nullable
  public static VirtualFile getVcsRootOrGuess(@NotNull Project project, @Nullable VirtualFile file) {
    VirtualFile root = null;
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (file != null) {
      if (fileIndex.isInLibrarySource(file) || fileIndex.isInLibraryClasses(file)) {
        LOG.debug("File is in library sources " + file);
        root = getVcsRootForLibraryFile(project, file);
      }
      else {
        LOG.debug("File is not in library sources " + file);
        root = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file);
      }
    }
    return root != null ? root : guessGitRoot(project);
  }

  @Nullable
  private static VirtualFile getVcsRootForLibraryFile(@NotNull Project project, @NotNull VirtualFile file) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    // for a file inside .jar/.zip consider the .jar/.zip file itself
    VirtualFile root = vcsManager.getVcsRootFor(VfsUtilCore.getVirtualFileForJar(file));
    if (root != null) {
      LOG.debug("Found root for zip/jar file: " + root);
      return root;
    }

    // for other libs which don't have jars inside the project dir (such as JDK) take the owner module of the lib
    List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(file);
    Set<VirtualFile> libraryRoots = new HashSet<VirtualFile>();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry || entry instanceof JdkOrderEntry) {
        VirtualFile moduleRoot = vcsManager.getVcsRootFor(entry.getOwnerModule().getModuleFile());
        if (moduleRoot != null) {
          libraryRoots.add(moduleRoot);
        }
      }
    }

    if (libraryRoots.size() == 0) {
      LOG.debug("No library roots");
      return null;
    }

    // if the lib is used in several modules, take the top module
    // (for modules of the same level we can't guess anything => take the first one)
    Iterator<VirtualFile> libIterator = libraryRoots.iterator();
    VirtualFile topLibraryRoot = libIterator.next();
    while (libIterator.hasNext()) {
      VirtualFile libRoot = libIterator.next();
      if (VfsUtilCore.isAncestor(libRoot, topLibraryRoot, true)) {
        topLibraryRoot = libRoot;
      }
    }
    LOG.debug("Several library roots, returning " + topLibraryRoot);
    return topLibraryRoot;
  }

  @Nullable
  private static VirtualFile guessGitRoot(@NotNull Project project) {
    LOG.debug("Guessing Git root...");
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    AbstractVcs gitVcs = GitVcs.getInstance(project);
    if (gitVcs == null) {
      LOG.debug("GitVcs not found.");
      return null;
    }
    VirtualFile[] gitRoots = vcsManager.getRootsUnderVcs(gitVcs);
    if (gitRoots.length == 0) {
      LOG.debug("No Git roots in the project.");
      return null;
    }

    if (gitRoots.length == 1) {
      VirtualFile onlyRoot = gitRoots[0];
      LOG.debug("Only one Git root in the project, returning: " + onlyRoot);
      return onlyRoot;
    }

    // remember the last visited Git root
    GitVcsSettings settings = GitVcsSettings.getInstance(project);
    if (settings != null) {
      String recentRootPath = settings.getRecentRootPath();
      if (recentRootPath != null) {
        VirtualFile recentRoot = VcsUtil.getVirtualFile(recentRootPath);
        if (recentRoot != null) {
          LOG.debug("Returning the recent root: " + recentRoot);
          return recentRoot;
        }
      }
    }

    // otherwise return the root of the project dir or the root containing the project dir, if there is such
    VirtualFile projectBaseDir = project.getBaseDir();
    if (projectBaseDir == null) {
      VirtualFile firstRoot = gitRoots[0];
      LOG.debug("Project base dir is null, returning the first root: " + firstRoot);
      return firstRoot;
    }
    VirtualFile rootCandidate = null;
    for (VirtualFile root : gitRoots) {
      if (root.equals(projectBaseDir)) {
        return root;
      }
      else if (VfsUtilCore.isAncestor(root, projectBaseDir, true)) {
        rootCandidate = root;
      }
    }
    LOG.debug("Returning the best candidate: " + rootCandidate);
    return rootCandidate;
  }

}
