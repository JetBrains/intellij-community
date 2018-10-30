// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package git4idea.staging;

import com.intellij.AppTopics;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffContentFactoryEx;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.icons.AllIcons;
import com.intellij.ide.dnd.*;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.ShowDiffPreviewAction;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.GenericNodeData;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitVcs;
import git4idea.checkin.GitCheckinEnvironment;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryFiles;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.diagnostic.Logger.getInstance;
import static com.intellij.openapi.vcs.changes.ui.ChangesTree.GROUP_BY_ACTION_GROUP;

public class GitIndexViewManager implements ChangesViewContentProvider {
  private static final Logger LOG = getInstance(GitIndexViewManager.class);

  private static final String PREVIEW_SPLITTER_PROPORTION = "GitIndexViewManager.DETAILS_SPLITTER_PROPORTION";
  private static final String PREVIEW_SPLITTER_KEY = "GitIndexViewManager.DETAILS_SPLITTER_KEY";
  private static final String COMMIT_SPLITTER_KEY = "GitIndexViewManager.COMMIT_SPLITTER_KEY";
  private final MyChangeProcessor myChangeProcessor;

  @NotNull private final Project myProject;
  @NotNull private final MergingUpdateQueue myUpdateQueue;

  private final MyChangesTree myTree;
  private final PreviewDiffSplitterComponent myDiffSplitter;
  private final CommitMessage myCommitMessagePanel;
  private final JBOptionButton myCommitButton;

  private final JPanel myPanel;
  private final JPanel myCommitPanel;
  private final Splitter myCommitSplitter;

  private boolean myModelUpdateInProgress = false;
  private State myState = new State();

  private boolean myShowUnversioned = false;

  @Nullable private Disposable myDisposable;

  public GitIndexViewManager(@NotNull Project project) {
    myProject = project;

    myTree = new MyChangesTree(project);
    myTree.addGroupingChangeListener(e -> myTree.rebuildTree());

    myChangeProcessor = new MyChangeProcessor(myProject);
    myDiffSplitter = new PreviewDiffSplitterComponent(myTree, myChangeProcessor, PREVIEW_SPLITTER_PROPORTION,
                                                      PropertiesComponent.getInstance().getBoolean(PREVIEW_SPLITTER_KEY, false));
    myTree.addSelectionListener(() -> {
      boolean fromModelRefresh = myModelUpdateInProgress;
      ApplicationManager.getApplication().invokeLater(() -> myDiffSplitter.updatePreview(fromModelRefresh));
    });

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, createToolbarActions(), false);
    toolbar.setTargetComponent(myTree);

    myCommitPanel = new JPanel(new BorderLayout());
    myCommitMessagePanel = new CommitMessage(project);

    myCommitPanel.add(myCommitMessagePanel, BorderLayout.CENTER);

    AbstractAction commitAction = new AbstractAction("Commit") {
      @Override
      public void actionPerformed(ActionEvent e) {
        performCommit(false);
      }
    };
    AbstractAction amendCommitAction = new AbstractAction("Amend") {
      @Override
      public void actionPerformed(ActionEvent e) {
        performCommit(true);
      }
    };
    myCommitButton = new JBOptionButton(commitAction, new Action[]{amendCommitAction});
    myCommitButton.addActionListener(e -> performCommit(false));
    myCommitButton.setEnabled(false);

    myCommitPanel.add(myCommitButton, BorderLayout.EAST);


    myCommitSplitter = new Splitter(true);
    myCommitSplitter.setFirstComponent(myDiffSplitter);

    if (PropertiesComponent.getInstance().getBoolean(COMMIT_SPLITTER_KEY, false)) {
      myCommitSplitter.setSecondComponent(myCommitPanel);
    }

    myPanel = JBUI.Panels.simplePanel(myCommitSplitter)
      .addToLeft(toolbar.getComponent());


    myUpdateQueue = new MergingUpdateQueue("GitIndexViewManager", 300, true, myTree, project);

    new MyDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), myTree);

    new MyDnDSupport().install();
  }

  @NotNull
  private ActionGroup createToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new MyRefreshAction());
    group.add(new MyDiffAction());
    group.add(new MyPartialAction());
    group.add(Separator.getInstance());
    group.add(ActionManager.getInstance().getAction(GROUP_BY_ACTION_GROUP));
    group.add(Separator.getInstance());
    group.add(new ToggleShowUnversionedAction());
    group.add(new ToggleDiffDetailsAction());
    group.add(new ToggleCommitDetailsAction());
    return group;
  }

  @Override
  public JComponent initContent() {
    assert myDisposable == null;
    myDisposable = Disposer.newDisposable();

    MessageBusConnection connection = myProject.getMessageBus().connect(myDisposable);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        List<GitRepository> repositories = GitRepositoryManager.getInstance(myProject).getRepositories();
        boolean indexFileModified = ContainerUtil.exists(repositories, repo -> {
          GitRepositoryFiles repositoryFiles = repo.getRepositoryFiles();
          return ContainerUtil.exists(events, e -> repositoryFiles.isIndexFile(e.getPath()));
        });
        if (indexFileModified) scheduleUpdate();
      }
    });

    MessageBusConnection appConnection = ApplicationManager.getApplication().getMessageBus().connect(myDisposable);
    appConnection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener() {
      @Override
      public void unsavedDocumentsDropped() {
        scheduleUpdate();
      }
    });

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
      @Override
      public void beforeDocumentChange(@NotNull DocumentEvent event) {
        Document document = event.getDocument();
        FileDocumentManager documentManager = FileDocumentManager.getInstance();
        VirtualFile file = documentManager.getFile(document);
        if (file != null && (file.isInLocalFileSystem() || file instanceof GitIndexVirtualFile)) {
          if (!documentManager.isDocumentUnsaved(document)) {
            scheduleUpdate();
          }
        }
      }
    }, myDisposable);

    ChangeListManager.getInstance(myProject).addChangeListListener(new ChangeListAdapter() {
      @Override
      public void changeListUpdateDone() {
        scheduleUpdate();
      }
    }, myDisposable);

    scheduleUpdate();
    return myPanel;
  }

  @Override
  public void disposeContent() {
    if (myDisposable != null) Disposer.dispose(myDisposable);
  }


  private void scheduleUpdate() {
    myTree.setPaintBusy(true);
    myUpdateQueue.queue(new Update("update") {
      @Override
      public void run() {
        updateModel();
      }
    });
  }

  private void updateModel() {
    BackgroundTaskUtil.executeAndTryWait(indicator -> {
      State newState = new State();

      for (GitRepository repository : GitRepositoryManager.getInstance(myProject).getRepositories()) {
        try {
          GitLineHandler h = new GitLineHandler(myProject, repository.getRoot(), GitCommand.STATUS);
          h.setSilent(true);
          h.addParameters("--porcelain", "-z", "--no-renames", "--ignored=no");
          h.addParameters("--untracked-files=" + (myShowUnversioned ? "all" : "no"));
          h.endOptions();

          String output = Git.getInstance().runCommand(h).getOutputOrThrow();
          parseOutput(repository, output, newState);
        }
        catch (VcsException e) {
          LOG.error(e);
        }
      }


      Set<FilePath> unstagedPaths = new HashSet<>();
      for (ModifiedFile file : newState.unstagedFiles) {
        unstagedPaths.add(file.filePath);
      }
      Set<FilePath> stagedPaths = new HashSet<>();
      for (ModifiedFile file : newState.stagedFiles) {
        stagedPaths.add(file.filePath);
      }

      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      for (Document document : fileDocumentManager.getUnsavedDocuments()) {
        VirtualFile vf = fileDocumentManager.getFile(document);
        if (vf == null || !vf.isValid()) continue;
        if (!fileDocumentManager.isFileModified(vf)) continue;

        if (vf.isInLocalFileSystem()) {
          FilePath filePath = VcsUtil.getFilePath(vf);
          if (unstagedPaths.contains(filePath)) continue;

          GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForFile(vf);
          if (repository == null) continue;

          newState.unstagedFiles.add(new ModifiedFile(repository, filePath, FileStatus.MODIFIED, Tag.UNSTAGED));
          unstagedPaths.add(filePath);
        }
        else if (vf instanceof GitIndexVirtualFile) {
          GitRepository repository = ((GitIndexVirtualFile)vf).getRepository();
          FilePath filePath = ((GitIndexVirtualFile)vf).getFilePath();

          if (!unstagedPaths.contains(filePath)) {
            newState.unstagedFiles.add(new ModifiedFile(repository, filePath, FileStatus.MODIFIED, Tag.UNSTAGED));
            unstagedPaths.add(filePath);
          }
          if (!stagedPaths.contains(filePath)) {
            newState.stagedFiles.add(new ModifiedFile(repository, filePath, FileStatus.MODIFIED, Tag.STAGED));
            stagedPaths.add(filePath);
          }
        }
      }

      return () -> {
        if (myUpdateQueue.isEmpty()) myTree.setPaintBusy(false);

        myModelUpdateInProgress = true;
        try {
          TreeState state = TreeState.createOn(myTree, (DefaultMutableTreeNode)myTree.getModel().getRoot());
          state.setScrollToSelection(false);

          myState = newState;
          myTree.rebuildTree();

          state.applyTo(myTree);
          myDiffSplitter.updatePreview(true);

          myCommitButton.setEnabled(myState.conflictedFiles.isEmpty());
        }
        finally {
          myModelUpdateInProgress = false;
        }
      };
    }, () -> myTree.setPaintBusy(true), 300, false);
  }

  private static void parseOutput(@NotNull GitRepository repository,
                                  @NotNull String output,
                                  @NotNull State newState) throws VcsException {
    VirtualFile root = repository.getRoot();
    final String[] split = output.split("\u0000");

    for (String line : split) {
      if (StringUtil.isEmptyOrSpaces(line)) continue;

      // format: XY_filename where _ stands for space.
      if (line.length() < 4) throw new VcsException("Can't parse status line: " + line);
      if (line.charAt(2) != ' ') throw new VcsException("Can't parse status line: " + line);

      final char xStatus = line.charAt(0);
      final char yStatus = line.charAt(1);
      final String path = line.substring(3); // skipping the space

      FilePath filePath = VcsUtil.getFilePath(root, path);

      if (xStatus == 'U' ||
          yStatus == 'U' ||
          xStatus == 'A' && yStatus == 'A' ||
          xStatus == 'D' && yStatus == 'D') {
        newState.conflictedFiles.add(new ModifiedFile(repository, filePath, FileStatus.MERGED_WITH_CONFLICTS, Tag.CONFLICTS));
      }
      else if (xStatus == '?') {
        assert yStatus == '?';
        newState.unstagedFiles.add(new ModifiedFile(repository, filePath, FileStatus.ADDED, Tag.UNSTAGED));
      }
      else {
        FileStatus xFileStatus = getStatus(xStatus);
        FileStatus yFileStatus = getStatus(yStatus);
        if (xFileStatus != null) newState.stagedFiles.add(new ModifiedFile(repository, filePath, xFileStatus, Tag.STAGED));
        if (yFileStatus != null) newState.unstagedFiles.add(new ModifiedFile(repository, filePath, yFileStatus, Tag.UNSTAGED));
      }
    }
  }

  @Nullable
  private static FileStatus getStatus(char status) throws VcsException {
    switch (status) {
      case ' ':
        return null;
      case 'M':
        return FileStatus.MODIFIED;
      case 'A':
        return FileStatus.ADDED;
      case 'D':
        return FileStatus.DELETED;
      default:
        throw new VcsException("Unexpected symbol as status: " + status);
    }
  }


  private class MyChangesTree extends ChangesTree {
    private MyChangesTree(Project project) {
      super(project, false, true);
    }

    @Override
    public void rebuildTree() {
      TreeModelBuilder builder = new TreeModelBuilder(myProject, getGroupingSupport().getGrouping());

      addNodesGroup(builder, myState.conflictedFiles, Tag.CONFLICTS, false);
      addNodesGroup(builder, myState.stagedFiles, Tag.STAGED, true);
      addNodesGroup(builder, myState.unstagedFiles, Tag.UNSTAGED, true);

      updateTreeModel(builder.build());
    }

    private void addNodesGroup(@NotNull TreeModelBuilder builder,
                               @NotNull List<ModifiedFile> files,
                               @NotNull Object tag,
                               boolean mandatory) {
      if (!mandatory && files.isEmpty()) return;
      builder.setGenericNodes(ContainerUtil.map(files, file -> new GenericNodeData(file.filePath, file.status, file)), tag);
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
        return VcsTreeModelData.selected(this)
          .userObjectsStream(ModifiedFile.class)
          .map(file -> file.filePath.getVirtualFile())
          .filter(Objects::nonNull)
          .toArray(VirtualFile[]::new);
      }
      if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
        return new VirtualFileDeleteProvider();
      }
      return super.getData(dataId);
    }
  }

  private class MyChangeProcessor extends ChangeViewDiffRequestProcessor {
    private MyChangeProcessor(@NotNull Project project) {
      super(project, DiffPlaces.CHANGES_VIEW);
      Disposer.register(project, this);
    }

    @NotNull
    @Override
    protected List<Wrapper> getSelectedChanges() {
      List<Wrapper> result = wrap(VcsTreeModelData.selected(myTree).userObjects(ModifiedFile.class));
      if (result.isEmpty()) result = getAllChanges();
      return result;
    }

    @NotNull
    @Override
    protected List<Wrapper> getAllChanges() {
      return wrap(VcsTreeModelData.all(myTree).userObjects(ModifiedFile.class));
    }

    @Override
    protected void selectChange(@NotNull Wrapper change) {
      DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTree.getModel().getRoot();
      DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, change.getUserObject());
      if (node != null) {
        TreeUtil.selectNode(myTree, node);
      }
    }

    @NotNull
    private List<Wrapper> wrap(@NotNull List<ModifiedFile> files) {
      return ContainerUtil.map(files, MyWrapper::new);
    }

    private class MyWrapper extends Wrapper {
      @NotNull private final ModifiedFile myFile;

      private MyWrapper(@NotNull ModifiedFile file) {
        myFile = file;
      }

      @NotNull
      @Override
      public Object getUserObject() {
        return myFile;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MyWrapper wrapper = (MyWrapper)o;
        return Objects.equals(myFile.filePath, wrapper.myFile.filePath) &&
               Objects.equals(myFile.tag, wrapper.myFile.tag);
      }

      @Override
      public int hashCode() {
        return Objects.hash(myFile.filePath, myFile.tag);
      }

      @Nullable
      @Override
      public DiffRequestProducer createProducer(@Nullable Project project) {
        return getDiffRequestProducer(myFile);
      }
    }
  }

  @Nullable
  private ChangeDiffRequestChain.Producer getDiffRequestProducer(@NotNull ModifiedFile file) {
    if (file.tag == Tag.STAGED) {
      return new SragedProducer(myProject, file);
    }
    if (file.tag == Tag.UNSTAGED) {
      return new UnstagedProducer(myProject, file);
    }
    return null;
  }

  @NotNull
  private static DiffContent getHeadContent(@NotNull Project project, @NotNull ModifiedFile file) throws VcsException, IOException {
    VirtualFile root = file.repository.getRoot();
    byte[] beforeContent = GitFileUtils.getFileContent(project, root, "HEAD", VcsFileUtil.relativePath(root, file.filePath));
    return DiffContentFactoryEx.getInstanceEx().createFromBytes(project, beforeContent, file.filePath);
  }

  @NotNull
  private static DiffContent getStagedContent(@NotNull Project project, @NotNull ModifiedFile file) throws VcsException {
    GitIndexVirtualFile indexFile = GitIndexManager.getInstance(project).getVirtualFile(file.repository, file.filePath);
    if (indexFile == null) throw new VcsException("Can't get staged file: " + file.filePath);
    return DiffContentFactory.getInstance().create(project, indexFile);
  }

  @NotNull
  private static DiffContent getLocalContent(@NotNull Project project, @NotNull ModifiedFile file) throws VcsException {
    VirtualFile localFile = file.filePath.getVirtualFile();
    if (localFile == null) throw new VcsException("Can't get local file: " + file.filePath);
    return DiffContentFactory.getInstance().create(project, localFile);
  }

  private static class UnstagedProducer extends ModifiedFileProducerBase {
    @NotNull private final Project myProject;

    private UnstagedProducer(@NotNull Project project, @NotNull ModifiedFile file) {
      super(file);
      myProject = project;
    }

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      try {
        DiffContentFactory factory = DiffContentFactory.getInstance();
        DiffContent content1 = myFile.status != FileStatus.ADDED ? getStagedContent(myProject, myFile) : factory.createEmpty();
        DiffContent content2 = myFile.status != FileStatus.DELETED ? getLocalContent(myProject, myFile) : factory.createEmpty();
        return new SimpleDiffRequest(null, content1, content2, "Staged", "Local");
      }
      catch (VcsException e) {
        throw new DiffRequestProducerException(e);
      }
    }
  }

  private static class SragedProducer extends ModifiedFileProducerBase {
    @NotNull private final Project myProject;

    private SragedProducer(@NotNull Project project, @NotNull ModifiedFile file) {
      super(file);
      myProject = project;
    }

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      try {
        DiffContentFactory factory = DiffContentFactory.getInstance();
        DiffContent content1 = myFile.status != FileStatus.ADDED ? getHeadContent(myProject, myFile) : factory.createEmpty();
        DiffContent content2 = myFile.status != FileStatus.DELETED ? getStagedContent(myProject, myFile) : factory.createEmpty();
        return new SimpleDiffRequest(null, content1, content2, "HEAD", "Staged");
      }
      catch (VcsException | IOException e) {
        throw new DiffRequestProducerException(e);
      }
    }
  }

  private class PartialFileProducer extends ModifiedFileProducerBase {
    private PartialFileProducer(@NotNull ModifiedFile file) {
      super(file);
    }

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      try {
        DiffContent content1 = getHeadContent(myProject, myFile);
        DiffContent content2 = getStagedContent(myProject, myFile);
        DiffContent content3 = getLocalContent(myProject, myFile);

        return new SimpleDiffRequest(null, content1, content2, content3, "HEAD", "Staged", "Local") {
          @Override
          public void onAssigned(boolean isAssigned) {
            super.onAssigned(isAssigned);

            if (!isAssigned && content2 instanceof DocumentContent) {
              Document document = ((DocumentContent)content2).getDocument();
              FileDocumentManager.getInstance().saveDocument(document);
            }
          }
        };
      }
      catch (VcsException | IOException e) {
        throw new DiffRequestProducerException(e);
      }
    }
  }

  private static abstract class ModifiedFileProducerBase implements ChangeDiffRequestChain.Producer {
    @NotNull protected final ModifiedFile myFile;

    private ModifiedFileProducerBase(@NotNull ModifiedFile file) {
      myFile = file;
    }

    @NotNull
    @Override
    public FilePath getFilePath() {
      return myFile.filePath;
    }

    @NotNull
    @Override
    public FileStatus getFileStatus() {
      return myFile.status;
    }

    @NotNull
    @Override
    public String getName() {
      return myFile.filePath.getPresentableUrl();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      // FIXME: drop cache on HEAD change
      ModifiedFileProducerBase wrapper = (ModifiedFileProducerBase)o;
      return Objects.equals(myFile.repository, wrapper.myFile.repository) &&
             Objects.equals(myFile.filePath, wrapper.myFile.filePath) &&
             Objects.equals(myFile.status, wrapper.myFile.status) &&
             Objects.equals(myFile.tag, wrapper.myFile.tag);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myFile.repository, myFile.filePath, myFile.status, myFile.tag);
    }
  }


  private class MyRefreshAction extends DumbAwareAction {
    private MyRefreshAction() {
      super("Refresh", null, AllIcons.Actions.Refresh);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      FileDocumentManager.getInstance().saveAllDocuments();
      GitIndexManager.getInstance(myProject).refresh(true);
      scheduleUpdate();
    }
  }

  private class MyDiffAction extends DumbAwareAction {
    private MyDiffAction() {
      super("Diff", null, AllIcons.Actions.Diff);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      ListSelection<Object> selection = VcsTreeModelData.getListSelectionOrAll(myTree);
      boolean canShowDiff = ContainerUtil.exists(selection.getList(), entry -> {
        return entry instanceof ModifiedFile && getDiffRequestProducer((ModifiedFile)entry) != null;
      });
      e.getPresentation().setEnabled(canShowDiff || !e.isFromActionToolbar());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ListSelection<Object> selection = VcsTreeModelData.getListSelectionOrAll(myTree);
      ListSelection<ChangeDiffRequestChain.Producer> producers = selection.map(it -> {
        if (it instanceof ModifiedFile) return getDiffRequestProducer((ModifiedFile)it);
        return null;
      });
      DiffRequestChain chain = new ChangeDiffRequestChain(producers.getList(), producers.getSelectedIndex());
      DiffManager.getInstance().showDiff(myProject, chain, new DiffDialogHints(null, myTree));
    }
  }

  private class MyPartialAction extends DumbAwareAction {
    private MyPartialAction() {
      super("Partial Commit", null, AllIcons.Vcs.Merge);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      long selected = VcsTreeModelData.selected(myTree)
        .userObjectsStream(ModifiedFile.class)
        .count();
      e.getPresentation().setEnabled(selected > 0);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Map<FilePath, ModifiedFile> stagedMap = new HashMap<>();
      VcsTreeModelData.selectedUnderTag(myTree, Tag.STAGED)
        .userObjectsStream(ModifiedFile.class)
        .forEach(file -> stagedMap.put(file.filePath, file));

      Map<FilePath, ModifiedFile> unstagedMap = new HashMap<>();
      VcsTreeModelData.selectedUnderTag(myTree, Tag.UNSTAGED)
        .userObjectsStream(ModifiedFile.class)
        .forEach(file -> unstagedMap.put(file.filePath, file));

      List<ChangeDiffRequestChain.Producer> producers = new ArrayList<>();
      for (FilePath path : ContainerUtil.union(stagedMap.keySet(), unstagedMap.keySet())) {
        ModifiedFile staged = stagedMap.get(path);
        ModifiedFile unstaged = unstagedMap.get(path);
        if (staged == null && unstaged == null) continue;
        if (staged != null && staged.status != FileStatus.MODIFIED) continue;
        if (unstaged != null && unstaged.status != FileStatus.MODIFIED) continue;

        ModifiedFile modifiedFile = ObjectUtils.chooseNotNull(staged, unstaged);
        producers.add(new PartialFileProducer(modifiedFile));
      }

      DiffRequestChain chain = new ChangeDiffRequestChain(producers, 0);
      DiffManager.getInstance().showDiff(myProject, chain, new DiffDialogHints(null, myTree));
    }
  }


  private void performCommit(boolean amend) {
    String commitMessage = myCommitMessagePanel.getComment();
    Set<GitRepository> repositories = ContainerUtil.map2Set(myState.stagedFiles, it -> it.repository);

    new Task.Backgroundable(myProject, "Committing Staged Changes...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (GitRepository repository : repositories) {
          Project project = repository.getProject();
          VirtualFile root = repository.getRoot();

          try {
            runWithMessageFile(project, root, commitMessage, commitMessageFile -> {
              GitLineHandler handler = new GitLineHandler(project, root, GitCommand.COMMIT);
              handler.setStdoutSuppressed(false);
              handler.addParameters("-F", commitMessageFile.getAbsolutePath());
              if (amend) {
                handler.addParameters("--amend");
              }
              handler.endOptions();
              Git.getInstance().runCommand(handler).getOutputOrThrow();

              repository.getRepositoryFiles().refresh();
              repository.update();
            });
          }
          catch (VcsException e) {
            LOG.error(e);
          }
        }
      }
    }.queue();
  }

  private static void runWithMessageFile(@NotNull Project project, @NotNull VirtualFile root, @NotNull String message,
                                         @NotNull ThrowableConsumer<File, VcsException> task) throws VcsException {
    File messageFile;
    try {
      messageFile = GitCheckinEnvironment.createCommitMessageFile(project, root, message);
    }
    catch (IOException ex) {
      throw new VcsException("Creation of commit message file failed", ex);
    }

    try {
      task.consume(messageFile);
    }
    finally {
      if (!messageFile.delete()) {
        LOG.warn("Failed to remove temporary file: " + messageFile);
      }
    }
  }


  private class ToggleShowUnversionedAction extends DumbAwareToggleAction {
    private ToggleShowUnversionedAction() {
      super("Show Unversioned", null, AllIcons.Vcs.ShowUnversionedFiles);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myShowUnversioned = state;
      scheduleUpdate();
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myShowUnversioned;
    }
  }

  private class ToggleDiffDetailsAction extends ShowDiffPreviewAction {
    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myDiffSplitter.setDetailsOn(state);
      PropertiesComponent.getInstance().setValue(PREVIEW_SPLITTER_KEY, state);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return PropertiesComponent.getInstance().getBoolean(PREVIEW_SPLITTER_KEY, false);
    }
  }

  private class ToggleCommitDetailsAction extends DumbAwareToggleAction {
    private ToggleCommitDetailsAction() {
      super("Show Commit Panel", null, AllIcons.Actions.PreviewDetailsVertically);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myCommitSplitter.setSecondComponent(state ? myCommitPanel : null);
      PropertiesComponent.getInstance().setValue(COMMIT_SPLITTER_KEY, state);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return PropertiesComponent.getInstance().getBoolean(COMMIT_SPLITTER_KEY, false);
    }
  }

  private class MyDnDSupport implements DnDDropHandler, DnDTargetChecker {
    private void install() {
      DnDSupport.createBuilder(myTree)
        .setTargetChecker(this)
        .setDropHandler(this)
        .setImageProvider(this::createDraggedImage)
        .setBeanProvider(this::createDragStartBean)
        .setDisposableParent(myProject)
        .install();
    }

    @NotNull
    private DnDImage createDraggedImage(@NotNull DnDActionInfo info) {
      long selectionCount = VcsTreeModelData.selectedUnderTag(myTree, Tag.STAGED).userObjectsStream().count() +
                            VcsTreeModelData.selectedUnderTag(myTree, Tag.UNSTAGED).userObjectsStream().count();


      String imageText = VcsBundle.message("changes.view.dnd.label", selectionCount);
      Image image = DnDAwareTree.getDragImage(myTree, imageText, null).getFirst();

      return new DnDImage(image, new Point(-image.getWidth(null), -image.getHeight(null)));
    }

    @Nullable
    private DnDDragStartBean createDragStartBean(@NotNull DnDActionInfo info) {
      DnDDragStartBean result = null;

      if (info.isMove()) {
        List<ModifiedFile> staged = VcsTreeModelData.selectedUnderTag(myTree, Tag.STAGED).userObjects(ModifiedFile.class);
        List<ModifiedFile> unstaged = VcsTreeModelData.selectedUnderTag(myTree, Tag.UNSTAGED).userObjects(ModifiedFile.class);

        if (!staged.isEmpty() || !unstaged.isEmpty()) {
          result = new DnDDragStartBean(new MyDragBean(myTree, staged, unstaged));
        }
      }

      return result;
    }

    @Override
    public boolean update(DnDEvent aEvent) {
      aEvent.hideHighlighter();
      aEvent.setDropPossible(false, "");

      Object attached = aEvent.getAttachedObject();
      ChangesBrowserNode dropNode = getDropRootNode(myTree, aEvent);
      if (dropNode == null) return true;

      if (attached instanceof MyDragBean) {
        final MyDragBean dragBean = (MyDragBean)attached;
        if (dragBean.getSourceComponent() == myTree && canAcceptDrop(dropNode, dragBean)) {
          dragBean.setTargetNode(dropNode);
          highlightDropNode(aEvent, dropNode);

          aEvent.setDropPossible(true);
          return false;
        }
      }
      return true;
    }

    @Nullable
    private ChangesBrowserNode getDropRootNode(@NotNull Tree tree, @NotNull DnDEvent event) {
      RelativePoint dropPoint = event.getRelativePoint();
      Point onTree = dropPoint.getPoint(tree);
      final TreePath dropPath = tree.getPathForLocation(onTree.x, onTree.y);

      if (dropPath == null) return null;

      ChangesBrowserNode dropNode = (ChangesBrowserNode)dropPath.getLastPathComponent();
      while (!dropNode.getParent().isRoot()) {
        dropNode = dropNode.getParent();
      }
      return dropNode;
    }

    private void highlightDropNode(@NotNull DnDEvent aEvent, @NotNull ChangesBrowserNode dropNode) {
      final Rectangle tableCellRect = myTree.getPathBounds(new TreePath(dropNode.getPath()));
      if (fitsInBounds(tableCellRect)) {
        aEvent.setHighlighting(new RelativeRectangle(myTree, tableCellRect), DnDEvent.DropTargetHighlightingType.RECTANGLE);
      }
    }

    private boolean fitsInBounds(final Rectangle rect) {
      final Container container = myTree.getParent();
      if (container instanceof JViewport) {
        final Container scrollPane = container.getParent();
        if (scrollPane instanceof JScrollPane) {
          final Rectangle rectangle = SwingUtilities.convertRectangle(myTree, rect, scrollPane.getParent());
          return scrollPane.getBounds().contains(rectangle);
        }
      }
      return true;
    }

    @Override
    public void drop(DnDEvent aEvent) {
      Object attached = aEvent.getAttachedObject();
      if (attached instanceof MyDragBean) {
        MyDragBean dragBean = (MyDragBean)attached;
        final ChangesBrowserNode changesBrowserNode = dragBean.getTargetNode();
        if (changesBrowserNode != null) {
          acceptDrop(changesBrowserNode, dragBean);
        }
      }
    }

    private boolean canAcceptDrop(@NotNull ChangesBrowserNode node, @NotNull MyDragBean bean) {
      Tag targetTag = ObjectUtils.tryCast(node.getUserObject(), Tag.class);
      if (targetTag == null) return false;

      if (targetTag == Tag.STAGED) {
        return !bean.myUnstaged.isEmpty();
      }
      if (targetTag == Tag.UNSTAGED) {
        return !bean.myStaged.isEmpty();
      }
      return false;
    }

    private void acceptDrop(@NotNull ChangesBrowserNode node, @NotNull MyDragBean bean) {
      try {
        Tag targetTag = ObjectUtils.tryCast(node.getUserObject(), Tag.class);
        if (targetTag == null) return;

        if (targetTag == Tag.STAGED) {
          FileDocumentManager.getInstance().saveAllDocuments();

          MultiMap<GitRepository, ModifiedFile> group = ContainerUtil.groupBy(bean.myUnstaged, file -> file.repository);
          for (GitRepository repository : group.keySet()) {
            List<FilePath> paths = ContainerUtil.map(group.get(repository), it -> it.filePath);

            GitFileUtils.addPaths(myProject, repository.getRoot(), paths);

            repository.getRepositoryFiles().refresh();
          }
        }

        if (targetTag == Tag.UNSTAGED) {
          FileDocumentManager.getInstance().saveAllDocuments();

          MultiMap<GitRepository, ModifiedFile> group = ContainerUtil.groupBy(bean.myStaged, file -> file.repository);
          for (GitRepository repository : group.keySet()) {
            List<FilePath> paths = ContainerUtil.map(group.get(repository), it -> it.filePath);

            for (List<String> chunk : VcsFileUtil.chunkPaths(repository.getRoot(), paths)) {
              final GitLineHandler handler = new GitLineHandler(myProject, repository.getRoot(), GitCommand.RESET);
              handler.addParameters(chunk);
              Git.getInstance().runCommand(handler).getOutputOrThrow();
            }

            repository.getRepositoryFiles().refresh();
          }
        }
      }
      catch (VcsException e) {
        LOG.error(e);
      }
    }
  }

  private static class MyDragBean {
    @NotNull private final ChangesTree myTree;
    @NotNull private final List<ModifiedFile> myStaged;
    @NotNull private final List<ModifiedFile> myUnstaged;

    private ChangesBrowserNode myTargetNode;

    private MyDragBean(@NotNull ChangesTree tree,
                       @NotNull List<ModifiedFile> staged,
                       @NotNull List<ModifiedFile> unstaged) {
      myTree = tree;
      myStaged = staged;
      myUnstaged = unstaged;
    }

    public JComponent getSourceComponent() {
      return myTree;
    }

    public ChangesBrowserNode getTargetNode() {
      return myTargetNode;
    }

    public void setTargetNode(final ChangesBrowserNode targetNode) {
      myTargetNode = targetNode;
    }
  }


  private static class IndexFileContentRevision implements ByteBackedContentRevision {
    private final GitIndexVirtualFile myIndexFile;

    private IndexFileContentRevision(@NotNull GitIndexVirtualFile indexFile) {
      myIndexFile = indexFile;
    }

    @Override
    public byte[] getContentAsBytes() throws VcsException {
      try {
        return myIndexFile.contentsToByteArray();
      }
      catch (IOException e) {
        throw new VcsException(e);
      }
    }

    @Nullable
    @Override
    public String getContent() throws VcsException {
      return ContentRevisionCache.getAsString(getContentAsBytes(), getFile(), null);
    }

    @NotNull
    @Override
    public FilePath getFile() {
      return myIndexFile.getFilePath();
    }

    @NotNull
    @Override
    public VcsRevisionNumber getRevisionNumber() {
      return VcsRevisionNumber.NULL;
    }
  }

  public static class VisibilityPredicate implements NotNullFunction<Project, Boolean> {
    @NotNull
    @Override
    public Boolean fun(Project project) {
      VcsRoot[] roots = ProjectLevelVcsManager.getInstance(project).getAllVcsRoots();
      return ContainerUtil.exists(roots, root -> GitVcs.getInstance(project).equals(root.getVcs()));
    }
  }


  private static class State {
    @NotNull public final List<ModifiedFile> stagedFiles = new ArrayList<>();
    @NotNull public final List<ModifiedFile> unstagedFiles = new ArrayList<>();
    @NotNull public final List<ModifiedFile> conflictedFiles = new ArrayList<>();
  }

  private static class ModifiedFile {
    @NotNull public final GitRepository repository;
    @NotNull public final FilePath filePath;
    @NotNull public final FileStatus status;
    @NotNull public final Tag tag;

    private ModifiedFile(@NotNull GitRepository repository, @NotNull FilePath filePath, @NotNull FileStatus status, @NotNull Tag tag) {
      this.repository = repository;
      this.filePath = filePath;
      this.status = status;
      this.tag = tag;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ModifiedFile file = (ModifiedFile)o;
      return Objects.equals(repository, file.repository) &&
             Objects.equals(filePath, file.filePath) &&
             Objects.equals(status, file.status) &&
             tag == file.tag;
    }

    @Override
    public int hashCode() {
      return Objects.hash(repository, filePath, status, tag);
    }
  }

  private enum Tag {
    STAGED("Staged"), UNSTAGED("Unstaged"), CONFLICTS("Conflicts");

    @NotNull private final String myText;

    Tag(@NotNull String text) {
      myText = text;
    }

    @Override
    public String toString() {
      return myText;
    }
  }
}
