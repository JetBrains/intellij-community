// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchFileHeaderInfo;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFilePatch;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.containers.ContainerUtil.map;

public class ApplyPatchDifferentiatedDialog extends DialogWrapper {
  @ApiStatus.Internal
  public static final String DIMENSION_SERVICE_KEY = "vcs.ApplyPatchDifferentiatedDialog";

  private static final Logger LOG = Logger.getInstance(ApplyPatchDifferentiatedDialog.class);
  private final ZipperUpdater myLoadQueue;
  private final TextFieldWithBrowseButton myPatchFile;

  private final List<AbstractFilePatchInProgress<?>> myPatches;
  private final List<? extends ShelvedBinaryFilePatch> myBinaryShelvedPatches;
  @NotNull private final EditorNotificationPanel myErrorNotificationPanel;
  @NotNull private final MyChangeTreeList myChangesTreeList;
  @NotNull private final JBLoadingPanel myChangesTreeLoadingPanel;
  @Nullable private final Collection<? extends Change> myPreselectedChanges;
  private final boolean myUseProjectRootAsPredefinedBase;

  private JComponent myCenterPanel;
  protected final Project myProject;

  private final AtomicReference<FilePresentationModel> myRecentPathFileChange;
  private final ApplyPatchDifferentiatedDialog.MyUpdater myUpdater;
  private final Runnable myReset;
  @Nullable private final ChangeListChooserPanel myChangeListChooser;
  private final ChangesLegendCalculator myInfoCalculator;
  private final CommitLegendPanel myCommitLegendPanel;
  private final ApplyPatchExecutor myCallback;
  private final List<? extends ApplyPatchExecutor> myExecutors;

  private boolean myContainBasedChanges;
  private JLabel myPatchFileLabel;
  private PatchReader myReader;
  private final boolean myCanChangePatchFile;
  private String myHelpId = "reference.dialogs.vcs.patch.apply"; //NON-NLS
  private final boolean myShouldUpdateChangeListName;

  public ApplyPatchDifferentiatedDialog(Project project, ApplyPatchExecutor<?> callback, List<? extends ApplyPatchExecutor<?>> executors,
                                        @NotNull ApplyPatchMode applyPatchMode, @NotNull VirtualFile patchFile) {
    this(project, callback, executors, applyPatchMode, patchFile, null, null, null, null, null, false);
  }

  public ApplyPatchDifferentiatedDialog(Project project,
                                        ApplyPatchExecutor<?> callback,
                                        List<? extends ApplyPatchExecutor<?>> executors,
                                        @NotNull ApplyPatchMode applyPatchMode,
                                        @NotNull List<? extends FilePatch> patches,
                                        @Nullable ChangeList defaultList) {
    this(project, callback, executors, applyPatchMode, null, patches, defaultList, null, null, null, false);
  }

  public ApplyPatchDifferentiatedDialog(Project project,
                                        ApplyPatchExecutor<?> callback,
                                        List<? extends ApplyPatchExecutor<?>> executors,
                                        @NotNull ApplyPatchMode applyPatchMode,
                                        @Nullable VirtualFile patchFile,
                                        @Nullable List<? extends FilePatch> patches,
                                        @Nullable ChangeList defaultList,
                                        @Nullable List<? extends ShelvedBinaryFilePatch> binaryShelvedPatches,
                                        @Nullable Collection<? extends Change> preselectedChanges,
                                        @Nullable @NlsSafe String externalCommitMessage,
                                        boolean useProjectRootAsPredefinedBase) {
    super(project, true);

    myCallback = callback;
    myExecutors = executors;
    myUseProjectRootAsPredefinedBase = useProjectRootAsPredefinedBase;
    setModal(false);
    setHorizontalStretch(2);
    setVerticalStretch(2);
    setTitle(applyPatchMode.getTitle());

    final FileChooserDescriptor descriptor = createSelectPatchDescriptor();
    descriptor.setTitle(VcsBundle.message("patch.apply.select.title"));

    myProject = project;
    myPatches = new ArrayList<>();
    myRecentPathFileChange = new AtomicReference<>();
    myBinaryShelvedPatches = binaryShelvedPatches;
    myPreselectedChanges = preselectedChanges;
    myErrorNotificationPanel = new EditorNotificationPanel(LightColors.RED, EditorNotificationPanel.Status.Error);
    cleanNotifications();
    myChangesTreeList = new MyChangeTreeList(project,
                                             new Runnable() {
                                               @Override
                                               public void run() {
                                                 final NamedLegendStatuses includedNameStatuses = new NamedLegendStatuses();
                                                 final Collection<AbstractFilePatchInProgress.PatchChange> includedChanges =
                                                   myChangesTreeList.getIncludedChanges();
                                                 final Set<Couple<String>> set = new HashSet<>();
                                                 for (AbstractFilePatchInProgress.PatchChange change : includedChanges) {
                                                   final FilePatch patch = change.getPatchInProgress().getPatch();
                                                   final Couple<String> pair = Couple.of(patch.getBeforeName(), patch.getAfterName());
                                                   if (set.contains(pair)) continue;
                                                   set.add(pair);
                                                   acceptChange(includedNameStatuses, change);
                                                 }
                                                 myInfoCalculator.setIncluded(includedNameStatuses);
                                                 myCommitLegendPanel.update();
                                                 updateOkActions();
                                               }
                                             }, new MyChangeNodeDecorator());
    myChangesTreeList.setDoubleClickAndEnterKeyHandler(() -> {
      List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      if (selectedChanges.size() == 1 && !selectedChanges.get(0).isValid()) {
        myChangesTreeList.handleInvalidChangesAndToggle();
      }
      new MyShowDiff().showDiff();
    });
    myChangesTreeLoadingPanel = new JBLoadingPanel(new BorderLayout(), getDisposable());
    myChangesTreeLoadingPanel.add(myChangesTreeList, BorderLayout.CENTER);
    myShouldUpdateChangeListName = defaultList == null && externalCommitMessage == null;
    myUpdater = new MyUpdater();
    myPatchFile = new TextFieldWithBrowseButton();
    myPatchFile.addBrowseFolderListener(VcsBundle.message("patch.apply.select.title"), "", project, descriptor);
    myPatchFile.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        setPathFileChangeDefault();
        queueRequest();
      }
    });

    myLoadQueue = new ZipperUpdater(500, Alarm.ThreadToUse.POOLED_THREAD, getDisposable());
    myCanChangePatchFile = applyPatchMode.isCanChangePatchFile();
    myReset = myCanChangePatchFile ? this::reset : EmptyRunnable.getInstance();

    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.areChangeListsEnabled()) {
      myChangeListChooser = new ChangeListChooserPanel(project, new Consumer<>() {
        @Override
        public void accept(final @Nullable @NlsContexts.DialogMessage String errorMessage) {
          setOKActionEnabled(errorMessage == null && isChangeTreeEnabled());
          setErrorText(errorMessage, myChangeListChooser);
        }
      });

      if (defaultList != null) {
        myChangeListChooser.setDefaultSelection(defaultList);
      }
      else if (externalCommitMessage != null) {
        myChangeListChooser.setSuggestedName(externalCommitMessage);
      }
      myChangeListChooser.init();
    }
    else {
      myChangeListChooser = null;
    }

    myInfoCalculator = new ChangesLegendCalculator();
    myCommitLegendPanel = new CommitLegendPanel(myInfoCalculator) {
      @Override
      public void update() {
        super.update();
        final int inapplicable = myInfoCalculator.getInapplicable();
        if (inapplicable > 0) {
          appendSpace();
          append(inapplicable, FileStatus.MERGED_WITH_CONFLICTS, VcsBundle.message("patch.apply.missing.base.file.label"));
        }
      }
    };

    init();

    if (patchFile != null && patchFile.isValid()) {
      patchFile.refresh(false, false);
      init(patchFile);
    }
    else if (patches != null) {
      init(patches);
    }

    myPatchFileLabel.setVisible(myCanChangePatchFile);
    myPatchFile.setVisible(myCanChangePatchFile);

    if (myCanChangePatchFile) {
      BulkFileListener listener = new BulkFileListener() {
        @Override
        public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
          for (VFileEvent event : events) {
            if (event instanceof VFileContentChangeEvent) {
              syncUpdatePatchFileAndScheduleReloadIfNeeded(event.getFile());
            }
          }
        }
      };
      ApplicationManager.getApplication().getMessageBus().connect(getDisposable())
        .subscribe(VirtualFileManager.VFS_CHANGES, listener);
    }
    updateOkActions();
  }

  private void updateOkActions() {
    boolean changeTreeEnabled = isChangeTreeEnabled();
    setOKActionEnabled(changeTreeEnabled);
    if (changeTreeEnabled) {
      if (myChangeListChooser != null) myChangeListChooser.updateEnabled();
    }
  }

  private boolean isChangeTreeEnabled() {
    return !myChangesTreeList.getIncludedChanges().isEmpty();
  }

  private void queueRequest() {
    paintBusy(true);
    myLoadQueue.queue(myUpdater);
  }

  private void init(@NotNull List<? extends FilePatch> patches) {
    List<AbstractFilePatchInProgress<?>> matchedPatches = new MatchPatchPaths(myProject).execute(patches, myUseProjectRootAsPredefinedBase);
    //todo add shelved binary patches
    ApplicationManager.getApplication().invokeLater(() -> {
      myPatches.clear();
      myPatches.addAll(matchedPatches);
      updateTree(true);
    });
  }

  public static FileChooserDescriptor createSelectPatchDescriptor() {
    return new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileSelectable(@Nullable VirtualFile file) {
        return file != null &&
               (FileTypeRegistry.getInstance().isFileOfType(file, PatchFileType.INSTANCE) ||
                FileTypeRegistry.getInstance().isFileOfType(file, FileTypes.PLAIN_TEXT));
      }
    };
  }

  @Override
  protected Action @NotNull [] createActions() {
    if (myExecutors.isEmpty()) {
      return super.createActions();
    }
    final List<Action> actions = new ArrayList<>(4);
    actions.add(getOKAction());
    for (int i = 0; i < myExecutors.size(); i++) {
      final ApplyPatchExecutor executor = myExecutors.get(i);
      final int finalI = i;
      actions.add(new AbstractAction(executor.getName()) {
        @Override
        public void actionPerformed(ActionEvent e) {
          runExecutor(executor);
          close(NEXT_USER_EXIT_CODE + finalI);
        }
      });
    }
    actions.add(getCancelAction());
    actions.add(getHelpAction());
    return actions.toArray(new Action[0]);
  }

  @RequiresEdt
  private void runExecutor(ApplyPatchExecutor<AbstractFilePatchInProgress<?>> executor) {
    Collection<AbstractFilePatchInProgress<?>> included = getIncluded();
    if (included.isEmpty()) {
      return;
    }
    MultiMap<VirtualFile, AbstractFilePatchInProgress<?>> patchGroups = new MultiMap<>();
    for (AbstractFilePatchInProgress<?> patchInProgress : included) {
      patchGroups.putValue(patchInProgress.getBase(), patchInProgress);
    }
    LocalChangeList targetChangelist = getSelectedChangeList();
    FilePresentationModel presentation = myRecentPathFileChange.get();
    VirtualFile vf = presentation != null ? presentation.getVf() : null;
    executor.apply(getOriginalRemaining(), patchGroups, targetChangelist, vf == null ? null : vf.getName(),
                   myReader == null ? null : myReader.getAdditionalInfo(ApplyPatchDefaultExecutor.pathsFromGroups(patchGroups)));
  }

  @NotNull
  private List<FilePatch> getOriginalRemaining() {
    Collection<AbstractFilePatchInProgress> notIncluded = ContainerUtil.subtract(myPatches, getIncluded());
    List<FilePatch> remainingOriginal = new ArrayList<>();
    for (AbstractFilePatchInProgress progress : notIncluded) {
      progress.reset();
      remainingOriginal.add(progress.getPatch());
    }
    return remainingOriginal;
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return DIMENSION_SERVICE_KEY;
  }

  @Override
  protected String getHelpId() {
    return myHelpId;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myChangeListChooser != null) return myChangeListChooser.getPreferredFocusedComponent();
    return myChangesTreeList;
  }

  private void setPathFileChangeDefault() {
    myRecentPathFileChange.set(new FilePresentationModel(myPatchFile.getText()));
  }

  private void init(@NotNull final VirtualFile patchFile) {
    myPatchFile.setText(patchFile.getPresentableUrl());
    myRecentPathFileChange.set(new FilePresentationModel(patchFile));
  }

  public void setHelpId(String s) {
    myHelpId = s;
  }

  private class MyUpdater implements Runnable {
    @Override
    public void run() {
      cleanNotifications();
      final FilePresentationModel filePresentationModel = myRecentPathFileChange.get();
      final VirtualFile file = filePresentationModel != null ? filePresentationModel.getVf() : null;
      if (file == null) {
        ApplicationManager.getApplication().invokeLater(myReset, ModalityState.stateForComponent(myCenterPanel));
        return;
      }
      myReader = loadPatches(file);
      final PatchFileHeaderInfo patchFileInfo = myReader != null ? myReader.getPatchFileInfo() : null;
      final String messageFromPatch = patchFileInfo != null ? patchFileInfo.getMessage() : null;
      VcsUser author = patchFileInfo != null ? patchFileInfo.getAuthor() : null;
      if (author != null && myChangeListChooser != null) {
        myChangeListChooser.setData(new ChangeListData(author));
      }
      List<FilePatch> filePatches = new ArrayList<>();
      if (myReader != null) {
        filePatches.addAll(myReader.getAllPatches());
      }
      if (!ContainerUtil.isEmpty(myBinaryShelvedPatches)) {
        filePatches.addAll(myBinaryShelvedPatches);
      }
      List<AbstractFilePatchInProgress<?>> matchedPatches =
        new MatchPatchPaths(myProject).execute(filePatches, myUseProjectRootAsPredefinedBase);

      ApplicationManager.getApplication().invokeLater(() -> {
        if (myShouldUpdateChangeListName && myChangeListChooser != null) {
          String subject = chooseNotNull(getSubjectFromMessage(messageFromPatch), file.getNameWithoutExtension().replace('_', ' ').trim());
          myChangeListChooser.setSuggestedName(subject, messageFromPatch, false);
        }
        myPatches.clear();
        myPatches.addAll(matchedPatches);
        updateTree(true);
        paintBusy(false);
        updateOkActions();
      }, ModalityState.stateForComponent(myCenterPanel));
    }
  }

  @Nullable
  private String getSubjectFromMessage(@Nullable String message) {
    return isEmptyOrSpaces(message) ? null : ChangeListUtil.createNameForChangeList(myProject, message);
  }

  @Nullable
  private PatchReader loadPatches(@NotNull VirtualFile patchFile) {
    try {
      String text = ReadAction.compute(() -> {
        try (InputStreamReader inputStreamReader = new InputStreamReader(patchFile.getInputStream(), patchFile.getCharset())) {
          return StreamUtil.readText(inputStreamReader);
        }
      });

      PatchReader reader = new PatchReader(text);
      reader.parseAllPatches();
      return reader;
    }
    catch (Exception e) {
      addNotificationAndWarn(VcsBundle.message("patch.apply.cannot.read.patch", patchFile.getPresentableName(), e.getMessage()));
      return null;
    }
  }

  private void addNotificationAndWarn(@NotNull @NlsContexts.Label String errorMessage) {
    LOG.warn(errorMessage);
    myErrorNotificationPanel.setText(errorMessage);
    myErrorNotificationPanel.setVisible(true);
  }

  private void cleanNotifications() {
    myErrorNotificationPanel.setText("");
    myErrorNotificationPanel.setVisible(false);
  }

  @RequiresEdt
  private void syncUpdatePatchFileAndScheduleReloadIfNeeded(@Nullable VirtualFile eventFile) {
    // if dialog is modal and refresh called not from dispatch thread then
    // fireEvents in RefreshQueueImpl will not be triggered because of wrong modality state inside those thread -> defaultMS == NON_MODAL
    final FilePresentationModel filePresentationModel = myRecentPathFileChange.get();
    VirtualFile filePresentationVf = filePresentationModel != null ? filePresentationModel.getVf() : null;
    if (filePresentationVf != null && (eventFile == null || filePresentationVf.equals(eventFile))) {
      filePresentationVf.refresh(false, false);
      queueRequest();
    }
  }

  private static class FilePresentationModel {
    @NotNull private final String myPath;
    @Nullable private VirtualFile myVf;

    private FilePresentationModel(@NotNull String path) {
      myPath = path;
      myVf = null; // don't try to find vf for each typing; only when requested
    }

    FilePresentationModel(@NotNull VirtualFile file) {
      myPath = file.getPath();
      myVf = file;
    }

    @Nullable
    public VirtualFile getVf() {
      if (myVf == null) {
        final VirtualFile file = VfsUtil.findFileByIoFile(new File(myPath), true);
        myVf = file != null && !file.isDirectory() ? file : null;
      }
      return myVf;
    }
  }

  private void reset() {
    myPatches.clear();
    myChangesTreeList.setChangesToDisplay(Collections.emptyList());
    myChangesTreeList.repaint();
    myContainBasedChanges = false;
    paintBusy(false);
  }

  @Override
  protected JComponent createCenterPanel() {
    if (myCenterPanel == null) {
      myCenterPanel = new JPanel(new GridBagLayout());
      final GridBagConstraints centralGb = createConstraints();

      myPatchFileLabel = new JLabel(VcsBundle.message("patch.apply.file.name.field"));
      myPatchFileLabel.setLabelFor(myPatchFile);
      myCenterPanel.add(myPatchFileLabel, centralGb);

      centralGb.fill = GridBagConstraints.HORIZONTAL;
      ++centralGb.gridy;
      myCenterPanel.add(myPatchFile, centralGb);

      JPanel treePanel = new JPanel(new GridBagLayout());
      final GridBagConstraints gb = createConstraints();

      final DefaultActionGroup group = new DefaultActionGroup();

      final MyShowDiff diffAction = new MyShowDiff();
      diffAction.registerCustomShortcutSet(CommonShortcuts.getDiff(), getRootPane());
      group.add(diffAction);

      ActionGroup mapDirectoryActionGroup =
        new ActionGroup(VcsBundle.message("patch.apply.change.directory.paths.group"), null, AllIcons.Vcs.Folders) {
          @Override
          public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
            return new AnAction[]{
              new MapDirectory(), new StripUp(IdeBundle.messagePointer("action.Anonymous.text.remove.leading.directory")), new ZeroStrip(),
              new StripDown(IdeBundle.messagePointer("action.Anonymous.text.restore.leading.directory")), new ResetStrip()};
          }
        };
      mapDirectoryActionGroup.setPopup(true);
      group.add(mapDirectoryActionGroup);

      if (myCanChangePatchFile) {
        group.add(new DumbAwareAction(VcsBundle.messagePointer("action.DumbAware.ApplyPatchDifferentiatedDialog.text.refresh"),
                                      VcsBundle.messagePointer("action.DumbAware.ApplyPatchDifferentiatedDialog.description.refresh"),
                                      AllIcons.Actions.Refresh) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            syncUpdatePatchFileAndScheduleReloadIfNeeded(null);
          }
        });
      }
      group.add(Separator.getInstance());
      group.add(ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP));

      final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("APPLY_PATCH", group, true);
      TreeActionsToolbarPanel toolbarPanel = new TreeActionsToolbarPanel(toolbar, myChangesTreeList);
      gb.fill = GridBagConstraints.HORIZONTAL;
      treePanel.add(toolbarPanel, gb);

      ++gb.gridy;
      gb.weighty = 1;
      gb.fill = GridBagConstraints.BOTH;
      JPanel changeTreePanel = JBUI.Panels.simplePanel(myChangesTreeLoadingPanel).addToTop(myErrorNotificationPanel);
      treePanel.add(ScrollPaneFactory.createScrollPane(changeTreePanel), gb);

      ++gb.gridy;
      gb.weighty = 0;
      gb.fill = GridBagConstraints.NONE;
      gb.insets.bottom = UIUtil.DEFAULT_VGAP;
      treePanel.add(myCommitLegendPanel.getComponent(), gb);

      ++gb.gridy;
      ++centralGb.gridy;
      centralGb.weighty = 1;
      centralGb.fill = GridBagConstraints.BOTH;
      if (myChangeListChooser != null) {
        Splitter splitter = new Splitter(true, 0.7f);
        splitter.setFirstComponent(treePanel);
        splitter.setSecondComponent(myChangeListChooser);
        myCenterPanel.add(splitter, centralGb);
      }
      else {
        myCenterPanel.add(treePanel, centralGb);
      }
    }
    return myCenterPanel;
  }

  @NotNull
  private static GridBagConstraints createConstraints() {
    return new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insets(1), 0, 0);
  }

  private void paintBusy(final boolean isBusy) {
    if (isBusy) {
      myChangesTreeList.setEmptyText("");
      myChangesTreeLoadingPanel.startLoading();
    }
    else {
      myChangesTreeList.setEmptyText(VcsBundle.message("commit.dialog.no.changes.detected.text"));
      myChangesTreeLoadingPanel.stopLoading();
    }
  }

  private final class MyChangeTreeList extends ChangesTreeImpl<AbstractFilePatchInProgress.PatchChange> {
    @Nullable private final ChangeNodeDecorator myChangeNodeDecorator;

    private MyChangeTreeList(Project project,
                             @Nullable Runnable inclusionListener,
                             @Nullable ChangeNodeDecorator decorator) {
      super(project, true, false, AbstractFilePatchInProgress.PatchChange.class);
      setInclusionListener(inclusionListener);
      myChangeNodeDecorator = decorator;
    }

    @NotNull
    @Override
    protected DefaultTreeModel buildTreeModel(@NotNull List<? extends AbstractFilePatchInProgress.PatchChange> changes) {
      return TreeModelBuilder.buildFromChanges(myProject, getGrouping(), changes, myChangeNodeDecorator);
    }

    @Override
    protected boolean isInclusionEnabled(@NotNull ChangesBrowserNode<?> node) {
      boolean enabled = super.isInclusionEnabled(node);
      Object value = node.getUserObject();
      if (value instanceof AbstractFilePatchInProgress.PatchChange) {
        enabled &= ((AbstractFilePatchInProgress.PatchChange)value).isValid();
      }
      return enabled;
    }

    @NotNull
    private List<AbstractFilePatchInProgress.PatchChange> getOnlyValidChanges(@NotNull Collection<? extends AbstractFilePatchInProgress.PatchChange> changes) {
      return ContainerUtil.filter(changes, AbstractFilePatchInProgress.PatchChange::isValid);
    }

    @Override
    protected boolean toggleChanges(@NotNull Collection<?> changes) {
      List<AbstractFilePatchInProgress.PatchChange> patchChanges =
        ContainerUtil.findAll(changes, AbstractFilePatchInProgress.PatchChange.class);

      if (patchChanges.size() == 1 && !patchChanges.get(0).isValid()) {
        return handleInvalidChangesAndToggle();
      }
      else {
        return super.toggleChanges(getOnlyValidChanges(patchChanges));
      }
    }

    private boolean handleInvalidChangesAndToggle() {
      new NewBaseSelector(false).run();
      return super.toggleChanges(getOnlyValidChanges(getSelectedChanges()));
    }
  }

  private final class MapDirectory extends DumbAwareAction {
    private final NewBaseSelector myNewBaseSelector;

    private MapDirectory() {
      super(VcsBundle.message("patch.apply.map.base.directory.action"));
      myNewBaseSelector = new NewBaseSelector();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      if ((selectedChanges.size() >= 1) && (sameBase(selectedChanges))) {
        final AbstractFilePatchInProgress.PatchChange patchChange = selectedChanges.get(0);
        final AbstractFilePatchInProgress patch = patchChange.getPatchInProgress();
        final List<VirtualFile> autoBases = patch.getAutoBasesCopy();
        if (autoBases.isEmpty() || (autoBases.size() == 1 && autoBases.get(0).equals(patch.getBase()))) {
          myNewBaseSelector.run();
        }
        else {
          autoBases.add(null);
          ListPopupStep<VirtualFile> step = new MapPopup(autoBases, myNewBaseSelector);
          JBPopupFactory.getInstance().createListPopup(step).showCenteredInCurrentWindow(myProject);
        }
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      e.getPresentation().setEnabled((selectedChanges.size() >= 1) && (sameBase(selectedChanges)));
    }
  }

  private static boolean sameBase(final List<? extends AbstractFilePatchInProgress.PatchChange> selectedChanges) {
    VirtualFile base = null;
    for (AbstractFilePatchInProgress.PatchChange change : selectedChanges) {
      final VirtualFile changeBase = change.getPatchInProgress().getBase();
      if (base == null) {
        base = changeBase;
      }
      else if (!base.equals(changeBase)) {
        return false;
      }
    }
    return true;
  }

  private void updateTree(boolean doInitCheck) {
    final List<AbstractFilePatchInProgress> patchesToSelect = changes2patches(myChangesTreeList.getSelectedChanges());
    final List<AbstractFilePatchInProgress.PatchChange> changes = getAllChanges();
    final Collection<AbstractFilePatchInProgress.PatchChange> included = getIncluded(doInitCheck, changes);

    myChangesTreeList.setIncludedChanges(included);
    myChangesTreeList.setChangesToDisplay(changes);
    if (doInitCheck) {
      myChangesTreeList.expandAll();
    }
    myChangesTreeList.repaint();
    if (!doInitCheck) {
      final List<AbstractFilePatchInProgress.PatchChange> toSelect =
        new ArrayList<>(patchesToSelect.size());
      for (AbstractFilePatchInProgress.PatchChange change : changes) {
        if (patchesToSelect.contains(change.getPatchInProgress())) {
          toSelect.add(change);
        }
      }
      myChangesTreeList.setSelectedChanges(toSelect);
    }

    myContainBasedChanges = false;
    for (AbstractFilePatchInProgress patch : myPatches) {
      if (patch.baseExistsOrAdded()) {
        myContainBasedChanges = true;
        break;
      }
    }
  }

  private List<AbstractFilePatchInProgress.PatchChange> getAllChanges() {
    return map(myPatches, AbstractFilePatchInProgress::getChange);
  }

  private static void acceptChange(final NamedLegendStatuses nameStatuses, final AbstractFilePatchInProgress.PatchChange change) {
    final AbstractFilePatchInProgress patchInProgress = change.getPatchInProgress();
    if (FilePatchStatus.ADDED.equals(patchInProgress.getStatus())) {
      nameStatuses.plusAdded();
    }
    else if (FilePatchStatus.DELETED.equals(patchInProgress.getStatus())) {
      nameStatuses.plusDeleted();
    }
    else {
      nameStatuses.plusModified();
    }
    if (!patchInProgress.baseExistsOrAdded()) {
      nameStatuses.plusInapplicable(); // may be deleted or modified, but still not applicable
    }
  }

  private Collection<AbstractFilePatchInProgress.PatchChange> getIncluded(boolean doInitCheck,
                                                                          List<? extends AbstractFilePatchInProgress.PatchChange> changes) {
    final NamedLegendStatuses totalNameStatuses = new NamedLegendStatuses();
    final NamedLegendStatuses includedNameStatuses = new NamedLegendStatuses();

    final Collection<AbstractFilePatchInProgress.PatchChange> included = new ArrayList<>();
    if (doInitCheck) {
      for (AbstractFilePatchInProgress.PatchChange change : changes) {
        acceptChange(totalNameStatuses, change);
        final AbstractFilePatchInProgress abstractFilePatchInProgress = change.getPatchInProgress();
        if (abstractFilePatchInProgress.baseExistsOrAdded() && (myPreselectedChanges == null || myPreselectedChanges.contains(change))) {
          acceptChange(includedNameStatuses, change);
          included.add(change);
        }
      }
    }
    else {
      // todo maybe written pretty
      final Collection<AbstractFilePatchInProgress.PatchChange> includedNow = myChangesTreeList.getIncludedChanges();
      final Set<AbstractFilePatchInProgress> toBeIncluded = new HashSet<>();
      for (AbstractFilePatchInProgress.PatchChange change : includedNow) {
        final AbstractFilePatchInProgress patch = change.getPatchInProgress();
        toBeIncluded.add(patch);
      }
      for (AbstractFilePatchInProgress.PatchChange change : changes) {
        final AbstractFilePatchInProgress patch = change.getPatchInProgress();
        acceptChange(totalNameStatuses, change);
        if (toBeIncluded.contains(patch) && patch.baseExistsOrAdded()) {
          acceptChange(includedNameStatuses, change);
          included.add(change);
        }
      }
    }
    myInfoCalculator.setTotal(totalNameStatuses);
    myInfoCalculator.setIncluded(includedNameStatuses);
    myCommitLegendPanel.update();
    return included;
  }

  private class NewBaseSelector implements Runnable {
    final boolean myDirectorySelector;

    NewBaseSelector() {
      this(true);
    }

    NewBaseSelector(boolean directorySelector) {
      myDirectorySelector = directorySelector;
    }

    @Override
    public void run() {
      final FileChooserDescriptor descriptor = myDirectorySelector
                                               ? FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                               : FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
      descriptor.setTitle(VcsBundle.message("patch.apply.select.base.title", myDirectorySelector ? 0 : 1));
      VirtualFile selectedFile = FileChooser.chooseFile(descriptor, myProject, null);
      if (selectedFile == null) {
        return;
      }

      final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      if (selectedChanges.size() >= 1) {
        for (AbstractFilePatchInProgress.PatchChange patchChange : selectedChanges) {
          final AbstractFilePatchInProgress patch = patchChange.getPatchInProgress();
          if (myDirectorySelector) {
            patch.setNewBase(selectedFile);
          }
          else {
            final FilePatch filePatch = patch.getPatch();
            //if file was renamed in the patch but applied on another or already renamed local one then we shouldn't apply this rename/move
            filePatch.setAfterName(selectedFile.getName());
            filePatch.setBeforeName(selectedFile.getName());
            patch.setNewBase(selectedFile.getParent());
          }
        }
        updateTree(false);
      }
    }
  }

  private static List<AbstractFilePatchInProgress> changes2patches(final List<? extends AbstractFilePatchInProgress.PatchChange> selectedChanges) {
    return map(selectedChanges, AbstractFilePatchInProgress.PatchChange::getPatchInProgress);
  }

  private final class MapPopup extends BaseListPopupStep<VirtualFile> {
    private final Runnable myNewBaseSelector;

    private MapPopup(final @NotNull List<? extends VirtualFile> aValues, Runnable newBaseSelector) {
      super(VcsBundle.message("path.apply.select.base.directory.for.a.path.popup"), aValues);
      myNewBaseSelector = newBaseSelector;
    }

    @Override
    public boolean isSpeedSearchEnabled() {
      return true;
    }

    @Override
    public PopupStep<?> onChosen(final VirtualFile selectedValue, boolean finalChoice) {
      if (selectedValue == null) {
        myNewBaseSelector.run();
        return null;
      }
      final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      if (selectedChanges.size() >= 1) {
        for (AbstractFilePatchInProgress.PatchChange patchChange : selectedChanges) {
          final AbstractFilePatchInProgress patch = patchChange.getPatchInProgress();
          patch.setNewBase(selectedValue);
        }
        updateTree(false);
      }
      return null;
    }

    @NotNull
    @Override
    public String getTextFor(VirtualFile value) {
      return value == null ? VcsBundle.message("patch.apply.select.base.for.a.path.message")
                           : value.getPath(); //NON-NLS
    }
  }

  private static class NamedLegendStatuses {
    private int myAdded;
    private int myModified;
    private int myDeleted;
    private int myInapplicable;

    NamedLegendStatuses() {
      myAdded = 0;
      myModified = 0;
      myDeleted = 0;
      myInapplicable = 0;
    }

    public void plusAdded() {
      ++myAdded;
    }

    public void plusModified() {
      ++myModified;
    }

    public void plusDeleted() {
      ++myDeleted;
    }

    public void plusInapplicable() {
      ++myInapplicable;
    }

    public int getAdded() {
      return myAdded;
    }

    public int getModified() {
      return myModified;
    }

    public int getDeleted() {
      return myDeleted;
    }

    public int getInapplicable() {
      return myInapplicable;
    }
  }

  private static final class ChangesLegendCalculator implements CommitLegendPanel.InfoCalculator {
    private NamedLegendStatuses myTotal;
    private NamedLegendStatuses myIncluded;

    private ChangesLegendCalculator() {
      myTotal = new NamedLegendStatuses();
      myIncluded = new NamedLegendStatuses();
    }

    public void setTotal(final NamedLegendStatuses nameStatuses) {
      myTotal = nameStatuses;
    }

    public void setIncluded(final NamedLegendStatuses nameStatuses) {
      myIncluded = nameStatuses;
    }

    @Override
    public int getNew() {
      return myTotal.getAdded();
    }

    @Override
    public int getModified() {
      return myTotal.getModified();
    }

    @Override
    public int getDeleted() {
      return myTotal.getDeleted();
    }

    @Override
    public int getUnversioned() {
      return 0;
    }

    public int getInapplicable() {
      return myTotal.getInapplicable();
    }

    @Override
    public int getIncludedNew() {
      return myIncluded.getAdded();
    }

    @Override
    public int getIncludedModified() {
      return myIncluded.getModified();
    }

    @Override
    public int getIncludedDeleted() {
      return myIncluded.getDeleted();
    }

    @Override
    public int getIncludedUnversioned() {
      return 0;
    }
  }

  private final class MyChangeNodeDecorator implements ChangeNodeDecorator {
    @Override
    public void decorate(@NotNull Change change, @NotNull SimpleColoredComponent component, boolean isShowFlatten) {
      if (change instanceof AbstractFilePatchInProgress.PatchChange patchChange) {
        final AbstractFilePatchInProgress patchInProgress = patchChange.getPatchInProgress();
        if (patchInProgress.getCurrentStrip() > 0) {
          component.append(VcsBundle.message("patch.apply.stripped.description", patchInProgress.getCurrentStrip()),
                           SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        }
        final String text;
        if (FilePatchStatus.ADDED.equals(patchInProgress.getStatus())) {
          text = VcsBundle.message("patch.apply.added.status");
        }
        else if (FilePatchStatus.DELETED.equals(patchInProgress.getStatus())) {
          text = VcsBundle.message("patch.apply.deleted.status");
        }
        else {
          text = VcsBundle.message("patch.apply.modified.status");
        }
        component.append("   ");
        component.append(text, SimpleTextAttributes.GRAY_ATTRIBUTES);
        if (!patchInProgress.baseExistsOrAdded()) {
          component.append("  ");
          component.append(VcsBundle.message("patch.apply.select.missing.base.link"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                           (Runnable)myChangesTreeList::handleInvalidChangesAndToggle);
        }
        else {
          if (!patchInProgress.getStatus().equals(FilePatchStatus.ADDED) && basePathWasChanged(patchInProgress)) {
            component.append("  ");
            component
              .append(VcsBundle.message("patch.apply.new.base.detected.node.description"), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
            component.setToolTipText(VcsBundle.message("patch.apply.old.new.base.info", patchInProgress.getOriginalBeforePath(),
                                                       myProject.getBasePath(), patchInProgress.getPatch().getBeforeName(),
                                                       patchInProgress.getBase().getPath()));
          }
        }
      }
    }

    @Override
    public void preDecorate(@NotNull Change change, @NotNull ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
    }
  }

  private boolean basePathWasChanged(@NotNull AbstractFilePatchInProgress patchInProgress) {
    return !FileUtil
      .filesEqual(patchInProgress.myIoCurrentBase, new File(myProject.getBasePath(), patchInProgress.getOriginalBeforePath()));
  }

  private Collection<AbstractFilePatchInProgress<?>> getIncluded() {
    return map(myChangesTreeList.getIncludedChanges(), AbstractFilePatchInProgress.PatchChange::getPatchInProgress);
  }

  @Nullable
  private LocalChangeList getSelectedChangeList() {
    return myChangeListChooser != null ? myChangeListChooser.getSelectedList(myProject) : null;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    runExecutor(myCallback);
  }

  private class ZeroStrip extends StripUp {
    ZeroStrip() {
      super(VcsBundle.messagePointer("action.Anonymous.text.remove.all.leading.directories"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myChangesTreeList.getSelectedChanges().forEach(change -> change.getPatchInProgress().setZero());
      updateTree(false);
    }
  }

  private class StripDown extends DumbAwareAction {
    StripDown(@NotNull Supplier<String> text) {
      super(text);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean isEnabled = ContainerUtil.exists(myChangesTreeList.getSelectedChanges(), change -> change.getPatchInProgress().canDown());
      e.getPresentation().setEnabled(isEnabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myChangesTreeList.getSelectedChanges().forEach(change -> change.getPatchInProgress().down());
      updateTree(false);
    }
  }

  private class StripUp extends DumbAwareAction {
    StripUp(Supplier<String> text) {
      super(text);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean isEnabled = ContainerUtil.exists(myChangesTreeList.getSelectedChanges(), change -> change.getPatchInProgress().canUp());
      e.getPresentation().setEnabled(isEnabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myChangesTreeList.getSelectedChanges().forEach(change -> change.getPatchInProgress().up());
      updateTree(false);
    }
  }

  private class ResetStrip extends StripDown {
    ResetStrip() {
      super(VcsBundle.messagePointer("action.Anonymous.text.restore.all.leading.directories"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myChangesTreeList.getSelectedChanges().forEach(change -> change.getPatchInProgress().reset());
      updateTree(false);
    }
  }

  private final class MyShowDiff extends DumbAwareAction {
    private final MyChangeComparator myMyChangeComparator;

    private MyShowDiff() {
      super(VcsBundle.message("action.name.show.difference"), null, AllIcons.Actions.Diff);
      myMyChangeComparator = new MyChangeComparator();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled((!myPatches.isEmpty()) && myContainBasedChanges);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      showDiff();
    }

    private void showDiff() {
      if (ChangeListManager.getInstance(myProject).isFreezedWithNotification(null)) return;
      if (myPatches.isEmpty() || (!myContainBasedChanges)) return;
      final List<AbstractFilePatchInProgress.PatchChange> changes = ContainerUtil.sorted(getAllChanges(), myMyChangeComparator);
      List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();

      if (changes.isEmpty()) return;
      final AbstractFilePatchInProgress.PatchChange selectedChange = !selectedChanges.isEmpty() ? selectedChanges.get(0) : changes.get(0);

      int selectedIdx = 0;
      List<ChangeDiffRequestChain.Producer> diffRequestPresentableList = new ArrayList<>();
      for (AbstractFilePatchInProgress.PatchChange change : changes) {
        diffRequestPresentableList.add(createDiffRequestProducer(change));

        if (change.equals(selectedChange)) {
          selectedIdx = diffRequestPresentableList.size() - 1;
        }
      }

      DiffRequestChain chain = new ChangeDiffRequestChain(diffRequestPresentableList, selectedIdx);
      DiffManager.getInstance().showDiff(myProject, chain, DiffDialogHints.DEFAULT);
    }

    @NotNull
    private ChangeDiffRequestChain.Producer createDiffRequestProducer(@NotNull AbstractFilePatchInProgress.PatchChange change) {
      AbstractFilePatchInProgress patchInProgress = change.getPatchInProgress();

      DiffRequestProducer delegate;
      if (!patchInProgress.baseExistsOrAdded()) {
        delegate = createBaseNotFoundErrorRequest(patchInProgress);
      }
      else {
        delegate = patchInProgress.getDiffRequestProducers(myProject, myReader);
      }

      return new MyProducerWrapper(delegate, change);
    }
  }

  @NotNull
  private static DiffRequestProducer createBaseNotFoundErrorRequest(@NotNull final AbstractFilePatchInProgress patchInProgress) {
    final String beforePath = patchInProgress.getPatch().getBeforeName();
    final String afterPath = patchInProgress.getPatch().getAfterName();
    return new DiffRequestProducer() {
      @NotNull
      @Override
      public String getName() {
        final File ioCurrentBase = patchInProgress.getIoCurrentBase();
        return ioCurrentBase == null ? patchInProgress.getCurrentPath() : ioCurrentBase.getPath();
      }

      @NotNull
      @Override
      public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
        throws DiffRequestProducerException, ProcessCanceledException {
        throw new DiffRequestProducerException(
          VcsBundle.message("changes.error.cannot.find.base.for.path", beforePath != null ? beforePath : afterPath));
      }
    };
  }

  private static final class MyProducerWrapper implements ChangeDiffRequestChain.Producer {
    private final DiffRequestProducer myProducer;
    private final Change myChange;

    private MyProducerWrapper(@NotNull DiffRequestProducer producer, @NotNull Change change) {
      myChange = change;
      myProducer = producer;
    }

    @NotNull
    @Override
    public String getName() {
      return myProducer.getName();
    }

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator) throws DiffRequestProducerException {
      return myProducer.process(context, indicator);
    }

    @NotNull
    @Override
    public FilePath getFilePath() {
      return ChangesUtil.getFilePath(myChange);
    }

    @NotNull
    @Override
    public FileStatus getFileStatus() {
      return myChange.getFileStatus();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MyProducerWrapper wrapper = (MyProducerWrapper)o;
      return Objects.equals(myProducer, wrapper.myProducer);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myProducer);
    }
  }

  private class MyChangeComparator implements Comparator<AbstractFilePatchInProgress.PatchChange> {
    @Override
    public int compare(AbstractFilePatchInProgress.PatchChange o1, AbstractFilePatchInProgress.PatchChange o2) {
      if (PropertiesComponent.getInstance(myProject).isTrueValue("ChangesBrowser.SHOW_FLATTEN")) {
        return o1.getPatchInProgress().getIoCurrentBase().getName().compareTo(o2.getPatchInProgress().getIoCurrentBase().getName());
      }
      return FileUtil.compareFiles(o1.getPatchInProgress().getIoCurrentBase(), o2.getPatchInProgress().getIoCurrentBase());
    }
  }
}
