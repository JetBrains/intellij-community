// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log;

import com.intellij.diff.DiffContentFactoryEx;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffVcsDataKeys;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.chains.SimpleDiffRequestProducer;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.CompareWithLocalDialog;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogDiffHandler;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.changes.GitChangeUtils;
import git4idea.diff.GitSubmoduleContentRevision;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitSubmodule;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import static com.intellij.diff.DiffRequestFactoryImpl.DIFF_TITLE_RENAME_SEPARATOR;
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

    FilePath filePath = chooseNotNull(leftPath, rightPath);
    if (filePath.isDirectory()) {
      showDiffForPaths(root, Collections.singleton(filePath), leftHash, rightHash);
    }
    else {
      DiffRequestProducer requestProducer = SimpleDiffRequestProducer.create(filePath, () -> {
        DiffContent leftDiffContent = createDiffContent(root, leftPath, leftHash);
        DiffContent rightDiffContent = createDiffContent(root, rightPath, rightHash);

        return new SimpleDiffRequest(getTitle(leftPath, rightPath, DIFF_TITLE_RENAME_SEPARATOR),
                                     leftDiffContent, rightDiffContent,
                                     leftHash.asString(), rightHash.asString());
      });
      SimpleDiffRequestChain chain = SimpleDiffRequestChain.fromProducer(requestProducer);
      UIUtil.invokeLaterIfNeeded(() -> DiffManager.getInstance().showDiff(myProject, chain, DiffDialogHints.DEFAULT));
    }
  }

  @Override
  public void showDiffWithLocal(@NotNull VirtualFile root, @Nullable FilePath revisionPath, @NotNull Hash revisionHash,
                                @NotNull FilePath localPath) {
    if (localPath.isDirectory()) {
      showDiffForPaths(root, Collections.singleton(localPath), revisionHash, null);
    }
    else {
      DiffRequestProducer requestProducer = SimpleDiffRequestProducer.create(localPath, () -> {
        DiffContent leftDiffContent = createDiffContent(root, revisionPath, revisionHash);
        DiffContent rightDiffContent = createCurrentDiffContent(localPath);
        return new SimpleDiffRequest(getTitle(revisionPath, localPath, DIFF_TITLE_RENAME_SEPARATOR),
                                     leftDiffContent, rightDiffContent,
                                     revisionHash.asString(),
                                     GitBundle.message("git.log.diff.handler.local.version.content.title"));
      });
      SimpleDiffRequestChain chain = SimpleDiffRequestChain.fromProducer(requestProducer);
      UIUtil.invokeLaterIfNeeded(() -> DiffManager.getInstance().showDiff(myProject, chain, DiffDialogHints.DEFAULT));
    }
  }

  @Override
  public void showDiffForPaths(@NotNull VirtualFile root,
                               @Nullable Collection<FilePath> affectedPaths,
                               @NotNull Hash leftRevision,
                               @Nullable Hash rightRevision) {
    UIUtil.invokeLaterIfNeeded(() -> {
      boolean isWithLocal = rightRevision == null;
      Collection<FilePath> filePaths = affectedPaths != null ? affectedPaths : Collections.singleton(VcsUtil.getFilePath(root));

      String leftRevisionTitle = leftRevision.toShortString();
      String rightRevisionTitle = isWithLocal
                                  ? GitBundle.message("git.log.diff.handler.local.version.name")
                                  : rightRevision.toShortString();
      String dialogTitle = affectedPaths != null
                           ? GitBundle.message("git.log.diff.handler.changes.between.revisions.in.paths.title",
                                               leftRevisionTitle, rightRevisionTitle, getTitleForPaths(root, affectedPaths))
                           : GitBundle.message("git.log.diff.handler.changes.between.revisions.title",
                                               leftRevisionTitle, rightRevisionTitle);
      CompareWithLocalDialog.LocalContent localContentSide = isWithLocal ? CompareWithLocalDialog.LocalContent.AFTER
                                                                         : CompareWithLocalDialog.LocalContent.NONE;

      CompareWithLocalDialog.showChanges(myProject, dialogTitle, localContentSide, () -> {
        if (isWithLocal) {
          return GitChangeUtils.getDiffWithWorkingDir(myProject, root, leftRevision.asString(), filePaths, false);
        }
        else {
          return GitChangeUtils.getDiff(myProject, root, leftRevision.asString(), rightRevision.asString(), filePaths);
        }
      });
    });
  }

  @NotNull
  private static String getTitleForPaths(@NotNull VirtualFile root, @NotNull Collection<? extends FilePath> filePaths) {
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

    diffContent.putUserData(DiffVcsDataKeys.REVISION_INFO, new Pair<>(path, revisionNumber));

    return diffContent;
  }

  @NotNull
  @Override
  public ContentRevision createContentRevision(@NotNull FilePath filePath, @NotNull Hash hash) {
    GitRevisionNumber revisionNumber = new GitRevisionNumber(hash.asString());
    return GitContentRevision.createRevision(filePath, revisionNumber, myProject);
  }
}
