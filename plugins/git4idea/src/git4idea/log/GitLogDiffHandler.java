// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log;

import com.intellij.diff.DiffContentFactoryEx;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogDiffHandler;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.changes.GitChangeUtils;
import git4idea.diff.GitSubmoduleContentRevision;
import git4idea.repo.GitSubmodule;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.intellij.diff.DiffRequestFactoryImpl.*;
import static com.intellij.util.ObjectUtils.chooseNotNull;

public class GitLogDiffHandler implements VcsLogDiffHandler {
  private static final Logger LOG = Logger.getInstance(GitLogDiffHandler.class);
  @NotNull private final Project myProject;
  @NotNull private final DiffContentFactoryEx myDiffContentFactory;

  public GitLogDiffHandler(@NotNull Project project) {
    myProject = project;
    myDiffContentFactory = DiffContentFactoryEx.getInstanceEx();
  }

  @Override
  public void showDiff(@NotNull VirtualFile root,
                       @Nullable FilePath leftPath,
                       @NotNull Hash leftHash,
                       @Nullable FilePath rightPath,
                       @NotNull Hash rightHash) {
    if (leftPath == null && rightPath == null) return;

    if (chooseNotNull(leftPath, rightPath).isDirectory()) {
      showDiffForPaths(root, Collections.singleton(chooseNotNull(leftPath, rightPath)), leftHash, rightHash);
    }
    else {
      loadDiffAndShow(new ThrowableComputable<DiffRequest, VcsException>() {
                        @Override
                        public DiffRequest compute() throws VcsException {
                          DiffContent leftDiffContent = createDiffContent(root, leftPath, leftHash);
                          DiffContent rightDiffContent = createDiffContent(root, rightPath, rightHash);

                          return new SimpleDiffRequest(getTitle(leftPath, rightPath, DIFF_TITLE_RENAME_SEPARATOR),
                                                       leftDiffContent, rightDiffContent,
                                                       leftHash.asString(), rightHash.asString());
                        }
                      },
                      request -> DiffManager.getInstance().showDiff(myProject, request),
                      "Calculating Diff for " + chooseNotNull(rightPath, leftPath).getName());
    }
  }

  @Override
  public void showDiffWithLocal(@NotNull VirtualFile root, @Nullable FilePath revisionPath, @NotNull Hash revisionHash,
                                @NotNull FilePath localPath) {
    if (localPath.isDirectory()) {
      showDiffForPaths(root, Collections.singleton(localPath), revisionHash, null);
    }
    else {
      loadDiffAndShow(new ThrowableComputable<DiffRequest, VcsException>() {
                        @Override
                        public DiffRequest compute() throws VcsException {
                          DiffContent leftDiffContent = createDiffContent(root, revisionPath, revisionHash);
                          DiffContent rightDiffContent = createCurrentDiffContent(localPath);
                          return new SimpleDiffRequest(getTitle(revisionPath, localPath, DIFF_TITLE_RENAME_SEPARATOR),
                                                       leftDiffContent, rightDiffContent,
                                                       revisionHash.asString(), "(Local)");
                        }
                      },
                      request -> DiffManager.getInstance().showDiff(myProject, request), "Calculating Diff for " + localPath.getName());
    }
  }

  @Override
  public void showDiffForPaths(@NotNull VirtualFile root,
                               @Nullable Collection<FilePath> affectedPaths,
                               @NotNull Hash leftRevision,
                               @Nullable Hash rightRevision) {
    Collection<FilePath> filePaths = affectedPaths != null ? affectedPaths : Collections.singleton(VcsUtil.getFilePath(root));
    loadDiffAndShow(() -> getDiff(root, filePaths, leftRevision, rightRevision),
                    (diff) -> {
                      String dialogTitle = "Changes between " +
                                           leftRevision.toShortString() +
                                           " and " +
                                           (rightRevision == null ? "local version" : rightRevision.toShortString()) +
                                           " in " +
                                           getTitleForPaths(root, affectedPaths);
                      VcsDiffUtil.showChangesDialog(myProject, dialogTitle, new ArrayList<>(diff));
                    },
                    "Calculating Diff for " +
                    StringUtil.shortenTextWithEllipsis(StringUtil.join(filePaths, FilePath::getName, ", "), 100, 0));
  }

  @NotNull
  private static String getTitleForPaths(@NotNull VirtualFile root, @Nullable Collection<? extends FilePath> filePaths) {
    if (filePaths == null) return getContentTitle(VcsUtil.getFilePath(root));
    String joinedPaths = StringUtil.join(filePaths, path -> VcsFileUtil.relativePath(root, path), ", ");
    return StringUtil.shortenTextWithEllipsis(joinedPaths, 100, 0);
  }

  @NotNull
  private DiffContent createCurrentDiffContent(@NotNull FilePath localPath) throws VcsException {
    GitSubmodule submodule = GitContentRevision.getRepositoryIfSubmodule(myProject, localPath);
    if (submodule != null) {
      ContentRevision revision = GitSubmoduleContentRevision.createCurrentRevision(submodule.getRepository());
      String content = revision.getContent();
      return content != null ? myDiffContentFactory.create(myProject, content) : myDiffContentFactory.createEmpty();
    }
    else {
      VirtualFile file = localPath.getVirtualFile();
      LOG.assertTrue(file != null);
      return myDiffContentFactory.create(myProject, file);
    }
  }

  @NotNull
  private Collection<Change> getDiff(@NotNull VirtualFile root,
                                     @NotNull Collection<? extends FilePath> filePaths,
                                     @NotNull Hash leftRevision,
                                     @Nullable Hash rightRevision) throws VcsException {
    if (rightRevision == null) {
      return GitChangeUtils.getDiffWithWorkingDir(myProject, root, leftRevision.asString(), filePaths, false);
    }
    return GitChangeUtils.getDiff(myProject, root, leftRevision.asString(), rightRevision.asString(), filePaths);
  }

  private <T> void loadDiffAndShow(@NotNull ThrowableComputable<? extends T, VcsException> load,
                                   @NotNull Consumer<? super T> show,
                                   @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, title + "...", false) {
        @Nullable private T myResult;

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            myResult = load.compute();
          }
          catch (VcsException e) {
            throw new RuntimeException(e);
          }
        }

        @Override
        public void onSuccess() {
          if (myResult != null) {
            show.consume(myResult);
          }
        }

        @Override
        public void onThrowable(@NotNull Throwable error) {
          VcsBalloonProblemNotifier.showOverVersionControlView(myProject, title + " failed\n" +
                                                                          error.getMessage(), MessageType.ERROR);
        }
      });
    }
    else {
      try {
        T result = load.compute();
        ApplicationManager.getApplication().invokeLater(() -> show.consume(result));
      }
      catch (VcsException e) {
        VcsBalloonProblemNotifier.showOverVersionControlView(myProject, title + " failed\n" +
                                                                        e.getMessage(), MessageType.ERROR);
      }
    }
  }

  @NotNull
  private DiffContent createDiffContent(@NotNull VirtualFile root,
                                        @Nullable FilePath path,
                                        @NotNull Hash hash) throws VcsException {

    DiffContent diffContent;
    GitRevisionNumber revisionNumber = new GitRevisionNumber(hash.asString());
    if (path == null) {
      diffContent = new EmptyContent();
    }
    else {
      GitSubmodule submodule = GitContentRevision.getRepositoryIfSubmodule(myProject, path);
      if (submodule != null) {
        ContentRevision revision = GitSubmoduleContentRevision.createRevision(submodule, revisionNumber);
        String content = revision.getContent();
        diffContent = content != null ? myDiffContentFactory.create(myProject, content) : myDiffContentFactory.createEmpty();
      }
      else {
        try {
          byte[] content = GitFileUtils.getFileContent(myProject, root, hash.asString(), VcsFileUtil.relativePath(root, path));
          diffContent = myDiffContentFactory.createFromBytes(myProject, content, path);
        }
        catch (IOException e) {
          throw new VcsException(e);
        }
      }
    }

    diffContent.putUserData(DiffUserDataKeysEx.REVISION_INFO, new Pair<>(path, revisionNumber));

    return diffContent;
  }

  @NotNull
  @Override
  public ContentRevision createContentRevision(@NotNull FilePath filePath, @NotNull Hash hash) {
    GitRevisionNumber revisionNumber = new GitRevisionNumber(hash.asString());
    return GitContentRevision.createRevision(filePath, revisionNumber, myProject);
  }
}
