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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.browser.LoadingChangesPanel;
import com.intellij.openapi.vcs.history.actions.GetVersionAction;
import com.intellij.openapi.vcs.history.actions.GetVersionAction.FileRevisionProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
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

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

      String rightRevisionTitle = isWithLocal
                                  ? GitBundle.message("git.log.diff.handler.local.version.name")
                                  : rightRevision.toShortString();
      String dialogTitle = GitBundle.message("git.log.diff.handler.paths.diff.title", leftRevision.toShortString(),
                                             rightRevisionTitle,
                                             getTitleForPaths(root, affectedPaths));

      Disposable loadingDisposable = Disposer.newDisposable();
      MyChangesBrowser changesBrowser = new MyChangesBrowser(myProject, isWithLocal);
      MyLoadingChangesPanel changesPanel = new MyLoadingChangesPanel(changesBrowser, loadingDisposable) {
        @NotNull
        @Override
        protected Collection<Change> loadChanges() throws VcsException {
          if (isWithLocal) {
            return GitChangeUtils.getDiffWithWorkingDir(myProject, root, leftRevision.asString(), filePaths, false);
          }
          else {
            return GitChangeUtils.getDiff(myProject, root, leftRevision.asString(), rightRevision.asString(), filePaths);
          }
        }
      };
      changesPanel.reloadChanges();

      DialogBuilder dialogBuilder = new DialogBuilder(myProject);
      dialogBuilder.setTitle(dialogTitle);
      dialogBuilder.setActionDescriptors(new DialogBuilder.CloseDialogAction());
      dialogBuilder.setCenterPanel(changesPanel);
      dialogBuilder.setPreferredFocusComponent(changesPanel.getChangesBrowser().getPreferredFocusedComponent());
      dialogBuilder.addDisposable(loadingDisposable);
      dialogBuilder.setDimensionServiceKey("Git.DiffForPathsDialog");
      dialogBuilder.showNotModal();
    });
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

  private static abstract class MyLoadingChangesPanel extends JPanel implements DataProvider {
    public static final DataKey<MyLoadingChangesPanel> DATA_KEY = DataKey.create("git4idea.log.MyLoadingChangesPanel");

    private final SimpleChangesBrowser myChangesBrowser;
    private final LoadingChangesPanel myLoadingPanel;

    private MyLoadingChangesPanel(@NotNull SimpleChangesBrowser changesBrowser, @NotNull Disposable disposable) {
      super(new BorderLayout());

      myChangesBrowser = changesBrowser;

      StatusText emptyText = myChangesBrowser.getViewer().getEmptyText();
      myLoadingPanel = new LoadingChangesPanel(myChangesBrowser, emptyText, disposable);
      add(myLoadingPanel, BorderLayout.CENTER);
    }

    @NotNull
    public SimpleChangesBrowser getChangesBrowser() {
      return myChangesBrowser;
    }

    public void reloadChanges() {
      myLoadingPanel.loadChangesInBackground(this::loadChanges, this::applyResult);
    }

    @NotNull
    protected abstract Collection<Change> loadChanges() throws VcsException;

    private void applyResult(@Nullable Collection<Change> changes) {
      myChangesBrowser.setChangesToDisplay(changes != null ? changes : Collections.emptyList());
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (DATA_KEY.is(dataId)) {
        return this;
      }
      return null;
    }
  }

  private static class MyChangesBrowser extends SimpleChangesBrowser {
    public final boolean myIsWithLocal;

    private MyChangesBrowser(@NotNull Project project, boolean isWithLocal) {
      super(project, false, true);
      myIsWithLocal = isWithLocal;
    }

    @NotNull
    @Override
    protected List<AnAction> createToolbarActions() {
      return ContainerUtil.append(
        super.createToolbarActions(),
        new MyGetVersionAction()
      );
    }

    @NotNull
    @Override
    protected List<AnAction> createPopupMenuActions() {
      return ContainerUtil.append(
        super.createPopupMenuActions(),
        new MyGetVersionAction()
      );
    }
  }

  private static class MyGetVersionAction extends DumbAwareAction {
    private MyGetVersionAction() {
      super(VcsBundle.messagePointer("action.name.get.file.content.from.repository"),
            VcsBundle.messagePointer("action.description.get.file.content.from.repository"), AllIcons.Actions.Download);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      MyLoadingChangesPanel changesPanel = e.getData(MyLoadingChangesPanel.DATA_KEY);
      if (project == null || changesPanel == null) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      MyChangesBrowser browser = ObjectUtils.tryCast(changesPanel.getChangesBrowser(), MyChangesBrowser.class);
      boolean isVisible = browser != null && browser.myIsWithLocal;
      boolean isEnabled = isVisible && !browser.getSelectedChanges().isEmpty();
      e.getPresentation().setVisible(isVisible);
      e.getPresentation().setEnabled(isEnabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = Objects.requireNonNull(e.getProject());
      MyLoadingChangesPanel changesPanel = e.getRequiredData(MyLoadingChangesPanel.DATA_KEY);

      List<FileRevisionProvider> fileContentProviders = ContainerUtil.map(changesPanel.getChangesBrowser().getSelectedChanges(),
                                                                          MyFileContentProvider::new);
      GetVersionAction.doGet(project, GitBundle.message("git.log.diff.handler.get.from.vcs.title"), fileContentProviders,
                             () -> changesPanel.reloadChanges());
    }

    private static class MyFileContentProvider implements FileRevisionProvider {
      @NotNull private final Change myChange;

      private MyFileContentProvider(@NotNull Change change) {
        myChange = change;
      }

      @NotNull
      @Override
      public FilePath getFilePath() {
        return ChangesUtil.getFilePath(myChange);
      }

      @Override
      public byte @Nullable [] getContent() throws VcsException {
        ContentRevision revision = myChange.getBeforeRevision();
        if (revision == null) return null;

        if (revision instanceof ByteBackedContentRevision) {
          byte[] bytes = ((ByteBackedContentRevision)revision).getContentAsBytes();
          if (bytes == null) throw new VcsException(VcsBundle.message("diff.action.executor.error.failed.to.load.content"));
          return bytes;
        }
        else {
          String content = revision.getContent();
          if (content == null) throw new VcsException(VcsBundle.message("diff.action.executor.error.failed.to.load.content"));
          return content.getBytes(getFilePath().getCharset());
        }
      }
    }
  }
}
