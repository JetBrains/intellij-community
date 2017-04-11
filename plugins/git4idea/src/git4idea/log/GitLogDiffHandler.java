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
import com.intellij.diff.DiffRequestFactoryImpl;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogDiffHandler;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitRevisionNumber;
import git4idea.changes.GitChangeUtils;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.ObjectUtils.notNull;

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
                       @NotNull Hash rightHash) throws VcsException {
    if (leftPath == null && rightPath == null) return;

    if (chooseNotNull(leftPath, rightPath).isDirectory()) {
      showDiffForDirectory(root, chooseNotNull(leftPath, rightPath), leftHash, rightHash);
    }
    else {
      try {
        DiffContent leftDiffContent = createDiffContent(root, leftPath, leftHash);
        DiffContent rightDiffContent = createDiffContent(root, rightPath, rightHash);

        DiffRequest request = new SimpleDiffRequest(getTitle(leftPath, rightPath),
                                                    leftDiffContent, rightDiffContent,
                                                    leftHash.asString(), rightHash.asString());

        WaitForProgressToShow.runOrInvokeLaterAboveProgress(() -> DiffManager.getInstance().showDiff(myProject, request), null, myProject);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public void showDiffWithLocal(@NotNull VirtualFile root, @Nullable FilePath revisionPath, @NotNull Hash revisionHash,
                                @NotNull FilePath localPath)
    throws VcsException {
    if (localPath.isDirectory()) {
      showDiffForDirectory(root, localPath, revisionHash, null);
    }
    else {
      try {
        DiffContent leftDiffContent = createDiffContent(root, revisionPath, revisionHash);

        VirtualFile file = localPath.getVirtualFile();
        LOG.assertTrue(file != null);
        DiffContent rightDiffContent = myDiffContentFactory.create(myProject, file);

        DiffRequest request = new SimpleDiffRequest(getTitle(revisionPath, localPath),
                                                    leftDiffContent, rightDiffContent,
                                                    revisionHash.asString(), "(Local)");

        WaitForProgressToShow.runOrInvokeLaterAboveProgress(() -> DiffManager.getInstance().showDiff(myProject, request), null, myProject);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private void showDiffForDirectory(@NotNull VirtualFile root,
                                    @NotNull FilePath directoryPath,
                                    @NotNull Hash leftRevision, @Nullable Hash rightRevision) throws VcsException {
    Collection<Change> diff = GitChangeUtils.getDiff(myProject, root,
                                                     leftRevision.asString(), rightRevision == null ? null : rightRevision.asString(),
                                                     Collections.singleton(directoryPath));
    WaitForProgressToShow.runOrInvokeLaterAboveProgress(() -> {
                                                          String dialogTitle = "Changes between " +
                                                                               leftRevision.asString() +
                                                                               " and " +
                                                                               (rightRevision == null ? "current revision" : rightRevision.asString()) +
                                                                               " in " +
                                                                               getTitle(directoryPath, directoryPath);
                                                          VcsDiffUtil.showChangesDialog(myProject, dialogTitle,
                                                                                        ContainerUtil.newArrayList(diff));
                                                        },
                                                        null,
                                                        myProject);
  }

  @NotNull
  private DiffContent createDiffContent(@NotNull VirtualFile root,
                                        @Nullable FilePath path,
                                        @NotNull Hash hash) throws IOException, VcsException {
    DiffContent diffContent;
    if (path == null) {
      diffContent = new EmptyContent();
    }
    else {
      byte[] content = GitFileUtils.getFileContent(myProject, root, hash.asString(), VcsFileUtil.relativePath(root, path));
      diffContent = myDiffContentFactory.createFromBytes(myProject, content, path);
    }

    diffContent.putUserData(DiffUserDataKeysEx.REVISION_INFO, new Pair<>(path, new GitRevisionNumber(hash.asString())));

    return diffContent;
  }

  @NotNull
  private static String getTitle(@Nullable FilePath leftPath, @Nullable FilePath rightPath) {
    LOG.assertTrue(leftPath != null || rightPath != null);

    if (Objects.equals(rightPath, leftPath)) {
      return DiffRequestFactoryImpl.getContentTitle(notNull(leftPath));
    }
    if (leftPath == null || rightPath == null) {
      return DiffRequestFactoryImpl.getContentTitle(chooseNotNull(leftPath, rightPath));
    }
    return DiffRequestFactoryImpl.getTitle(leftPath, rightPath, " -> ");
  }
}
