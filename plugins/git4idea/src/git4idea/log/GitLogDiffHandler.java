/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogDiffHandler;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitRevisionNumber;
import git4idea.changes.GitChangeUtils;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;

import static com.intellij.diff.DiffRequestFactoryImpl.getTitle;
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
      showDiffForDirectory(root, chooseNotNull(leftPath, rightPath), leftHash, rightHash);
    }
    else {
      loadDiffAndShow(new ThrowableComputable<DiffRequest, VcsException>() {
                        @Override
                        public DiffRequest compute() throws VcsException {
                          DiffContent leftDiffContent = createDiffContent(root, leftPath, leftHash);
                          DiffContent rightDiffContent = createDiffContent(root, rightPath, rightHash);

                          return new SimpleDiffRequest(getTitle(leftPath, rightPath, " -> "),
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
      showDiffForDirectory(root, localPath, revisionHash, null);
    }
    else {
      loadDiffAndShow(new ThrowableComputable<DiffRequest, VcsException>() {
                        @Override
                        public DiffRequest compute() throws VcsException {
                          DiffContent leftDiffContent = createDiffContent(root, revisionPath, revisionHash);

                          VirtualFile file = localPath.getVirtualFile();
                          LOG.assertTrue(file != null);
                          DiffContent rightDiffContent = myDiffContentFactory.create(myProject, file);

                          return new SimpleDiffRequest(getTitle(revisionPath, localPath, " -> "),
                                                       leftDiffContent, rightDiffContent,
                                                       revisionHash.asString(), "(Local)");
                        }
                      },
                      request -> DiffManager.getInstance().showDiff(myProject, request), "Calculating Diff for " + localPath.getName());
    }
  }

  private void showDiffForDirectory(@NotNull VirtualFile root,
                                    @NotNull FilePath directoryPath,
                                    @NotNull Hash leftRevision, @Nullable Hash rightRevision) {
    loadDiffAndShow(() -> GitChangeUtils.getDiff(myProject, root,
                                                 leftRevision.asString(), rightRevision == null ? null : rightRevision.asString(),
                                                 Collections.singleton(directoryPath)),
                    (diff) -> {
                      String dialogTitle = "Changes between " +
                                           leftRevision.asString() +
                                           " and " +
                                           (rightRevision == null ? "current revision" : rightRevision.asString()) +
                                           " in " +
                                           getTitle(directoryPath, directoryPath, " -> ");
                      VcsDiffUtil.showChangesDialog(myProject, dialogTitle, ContainerUtil.newArrayList(diff));
                    }, "Calculating Diff for " + directoryPath.getName());
  }

  private <T> void loadDiffAndShow(@NotNull ThrowableComputable<T, VcsException> load,
                                   @NotNull Consumer<T> show,
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
    if (path == null) {
      diffContent = new EmptyContent();
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

    diffContent.putUserData(DiffUserDataKeysEx.REVISION_INFO, new Pair<>(path, new GitRevisionNumber(hash.asString())));

    return diffContent;
  }
}
