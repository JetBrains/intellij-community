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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.DiffRequestChainBase;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeGoToChangePopupAction;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFilePatch;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.*;
import com.intellij.ui.*;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.NullableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.containers.ContainerUtil.map;

public class ApplyPatchDifferentiatedDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(ApplyPatchDifferentiatedDialog.class);
  private final ZipperUpdater myLoadQueue;
  private final TextFieldWithBrowseButton myPatchFile;

  private final List<AbstractFilePatchInProgress> myPatches;
  private final List<ShelvedBinaryFilePatch> myBinaryShelvedPatches;
  @NotNull private EditorNotificationPanel myErrorNotificationPanel;
  @NotNull private final MyChangeTreeList myChangesTreeList;
  @Nullable private final Collection<Change> myPreselectedChanges;
  private final boolean myUseProjectRootAsPredefinedBase;

  private JComponent myCenterPanel;
  protected final Project myProject;

  private final AtomicReference<FilePresentationModel> myRecentPathFileChange;
  private final ApplyPatchDifferentiatedDialog.MyUpdater myUpdater;
  private final Runnable myReset;
  private final ChangeListChooserPanel myChangeListChooser;
  private final ChangesLegendCalculator myInfoCalculator;
  private final CommitLegendPanel myCommitLegendPanel;
  private final ApplyPatchExecutor myCallback;
  private final List<ApplyPatchExecutor> myExecutors;

  private boolean myContainBasedChanges;
  private JLabel myPatchFileLabel;
  private PatchReader myReader;
  private VirtualFileListener myListener;
  private final boolean myCanChangePatchFile;
  private String myHelpId = "reference.dialogs.vcs.patch.apply";
  private final boolean myShouldUpdateChangeListName;

  public ApplyPatchDifferentiatedDialog(final Project project, final ApplyPatchExecutor callback, final List<ApplyPatchExecutor> executors,
                                        @NotNull final ApplyPatchMode applyPatchMode, @NotNull final VirtualFile patchFile) {
    this(project, callback, executors, applyPatchMode, patchFile, null, null, null, null, null, false);
  }

  public ApplyPatchDifferentiatedDialog(final Project project,
                                        final ApplyPatchExecutor callback,
                                        final List<ApplyPatchExecutor> executors,
                                        @NotNull final ApplyPatchMode applyPatchMode,
                                        @NotNull final List<TextFilePatch> patches,
                                        @Nullable final LocalChangeList defaultList) {
    this(project, callback, executors, applyPatchMode, null, patches, defaultList, null, null, null, false);
  }

  public ApplyPatchDifferentiatedDialog(final Project project,
                                        final ApplyPatchExecutor callback,
                                        final List<ApplyPatchExecutor> executors,
                                        @NotNull final ApplyPatchMode applyPatchMode,
                                        @Nullable final VirtualFile patchFile,
                                        @Nullable final List<TextFilePatch> patches,
                                        @Nullable final LocalChangeList defaultList,
                                        @Nullable List<ShelvedBinaryFilePatch> binaryShelvedPatches,
                                        @Nullable Collection<Change> preselectedChanges,
                                        @Nullable String externalCommitMessage,
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
    myPatches = new LinkedList<>();
    myRecentPathFileChange = new AtomicReference<>();
    myBinaryShelvedPatches = binaryShelvedPatches;
    myPreselectedChanges = preselectedChanges;
    myErrorNotificationPanel = new EditorNotificationPanel(LightColors.RED);
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
    myChangesTreeList.setDoubleClickHandler(() -> {
      List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      if (selectedChanges.size() == 1 && !selectedChanges.get(0).isValid()) {
        myChangesTreeList.handleInvalidChangesAndToggle();
      }
      new MyShowDiff().showDiff();
    });
    myShouldUpdateChangeListName = defaultList == null && externalCommitMessage == null;
    myUpdater = new MyUpdater();
    myPatchFile = new TextFieldWithBrowseButton();
    myPatchFile.addBrowseFolderListener(VcsBundle.message("patch.apply.select.title"), "", project, descriptor);
    myPatchFile.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        setPathFileChangeDefault();
        queueRequest();
      }
    });

    myLoadQueue = new ZipperUpdater(500, Alarm.ThreadToUse.POOLED_THREAD, getDisposable());
    myCanChangePatchFile = applyPatchMode.isCanChangePatchFile();
    myReset = myCanChangePatchFile ? this::reset : EmptyRunnable.getInstance();

    myChangeListChooser = new ChangeListChooserPanel(project, new NullableConsumer<String>() {
      @Override public void consume(@Nullable String errorMessage) {
        setOKActionEnabled(errorMessage == null && isChangeTreeEnabled());
        setErrorText(errorMessage, myChangeListChooser);
      }
    });

    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    myChangeListChooser.setChangeLists(changeListManager.getChangeListsCopy());
    if (defaultList != null) {
      myChangeListChooser.setDefaultSelection(defaultList);
    }
    else if (externalCommitMessage != null) {
      myChangeListChooser.setSuggestedName(externalCommitMessage);
    }
    myChangeListChooser.init();

    myInfoCalculator = new ChangesLegendCalculator();
    myCommitLegendPanel = new CommitLegendPanel(myInfoCalculator) {
      @Override
      public void update() {
        super.update();
        final int inapplicable = myInfoCalculator.getInapplicable();
        if (inapplicable > 0) {
          appendSpace();
          appendText(inapplicable, inapplicable, FileStatus.MERGED_WITH_CONFLICTS, "Missing Base:");
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
      myListener = new VirtualFileListener() {
        @Override
        public void contentsChanged(@NotNull VirtualFileEvent event) {
          syncUpdatePatchFileAndScheduleReloadIfNeeded(event.getFile());
        }
      };
      final VirtualFileManager fileManager = VirtualFileManager.getInstance();
      fileManager.addVirtualFileListener(myListener);
      Disposer.register(getDisposable(), () -> fileManager.removeVirtualFileListener(myListener));
    }
    updateOkActions();
  }

  private void updateOkActions() {
    boolean changeTreeEnabled = isChangeTreeEnabled();
    setOKActionEnabled(changeTreeEnabled);
    if (changeTreeEnabled) {
      myChangeListChooser.updateEnabled();
    }
  }

  private boolean isChangeTreeEnabled() {
    return !myChangesTreeList.getIncludedChanges().isEmpty();
  }

  private void queueRequest() {
    paintBusy(true);
    myLoadQueue.queue(myUpdater);
  }

  private void init(List<? extends FilePatch> patches) {
    final List<AbstractFilePatchInProgress> matchedPatches =
      new MatchPatchPaths(myProject).execute(patches, myUseProjectRootAsPredefinedBase);
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
      public boolean isFileSelectable(VirtualFile file) {
        return file.getFileType() == StdFileTypes.PATCH || file.getFileType() == FileTypes.PLAIN_TEXT;
      }
    };
  }

  @NotNull
  @Override
  protected Action[] createActions() {
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
    return actions.toArray(new Action[actions.size()]);
  }

  @CalledInAwt
  private void runExecutor(ApplyPatchExecutor executor) {
    final Collection<AbstractFilePatchInProgress> included = getIncluded();
    if (included.isEmpty()) return;
    final MultiMap<VirtualFile, AbstractFilePatchInProgress> patchGroups = new MultiMap<>();
    for (AbstractFilePatchInProgress patchInProgress : included) {
      patchGroups.putValue(patchInProgress.getBase(), patchInProgress);
    }
    final LocalChangeList selected = getSelectedChangeList();
    FilePresentationModel presentation = myRecentPathFileChange.get();
    VirtualFile vf = presentation != null ? presentation.getVf() : null;
    executor.apply(getOriginalRemaining(), patchGroups, selected, vf == null ? null : vf.getName(),
                   myReader == null ? null : myReader.getAdditionalInfo(ApplyPatchDefaultExecutor.pathsFromGroups(patchGroups)));
  }

  @NotNull
  private List<FilePatch> getOriginalRemaining() {
    Collection<AbstractFilePatchInProgress> notIncluded = ContainerUtil.subtract(myPatches, getIncluded());
    List<FilePatch> remainingOriginal = ContainerUtil.newArrayList();
    for (AbstractFilePatchInProgress progress : notIncluded) {
      progress.reset();
      remainingOriginal.add(progress.getPatch());
    }
    return remainingOriginal;
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "vcs.ApplyPatchDifferentiatedDialog";
  }

  @Override
  protected String getHelpId() {
    return myHelpId;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myChangeListChooser.getPreferredFocusedComponent();
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
      if (author != null) {
        myChangeListChooser.setData(new ChangeListData(author));
      }
      List<FilePatch> filePatches = ContainerUtil.newArrayList();
      if (myReader != null) {
        filePatches.addAll(myReader.getAllPatches());
      }
      if (!ContainerUtil.isEmpty(myBinaryShelvedPatches)) {
        filePatches.addAll(myBinaryShelvedPatches);
      }
      final List<AbstractFilePatchInProgress> matchedPatches =
        new MatchPatchPaths(myProject).execute(filePatches, myUseProjectRootAsPredefinedBase);

      ApplicationManager.getApplication().invokeLater(() -> {
        if (myShouldUpdateChangeListName) {
          myChangeListChooser.setSuggestedName(
            chooseNotNull(getSubjectFromMessage(messageFromPatch), file.getNameWithoutExtension().replace('_', ' ').trim()),
            messageFromPatch);
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
    PatchReader reader;
    try {
      reader = ApplicationManager.getApplication().runReadAction(new ThrowableComputable<PatchReader, IOException>() {
        @Override
        public PatchReader compute() throws IOException {
          return PatchVirtualFileReader.create(patchFile);
        }
      });
    }
    catch (IOException e) {
      addNotificationAndWarn("Can't read patchFile " + patchFile.getPresentableName() + ": " + e.getMessage());
      return null;
    }
    try {
      reader.parseAllPatches();
    }
    catch (PatchSyntaxException e) {
      addNotificationAndWarn("Can't read patch: " + e.getMessage());
      return null;
    }

    return reader;
  }

  private void addNotificationAndWarn(@NotNull String errorMessage) {
    LOG.warn(errorMessage);
    myErrorNotificationPanel.setText(errorMessage);
    myErrorNotificationPanel.setVisible(true);
  }

  private void cleanNotifications() {
    myErrorNotificationPanel.setText("");
    myErrorNotificationPanel.setVisible(false);
  }

  @CalledInAwt
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

    public FilePresentationModel(@NotNull VirtualFile file) {
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
      final AnAction[] treeActions = myChangesTreeList.getTreeActions();
      group.addAll(treeActions);
      group.add(new MapDirectory());

      final MyShowDiff diffAction = new MyShowDiff();
      diffAction.registerCustomShortcutSet(CommonShortcuts.getDiff(), getRootPane());
      group.add(diffAction);

      group.add(new StripUp());
      group.add(new StripDown());
      group.add(new ResetStrip());
      group.add(new ZeroStrip());
      if (myCanChangePatchFile) {
        group.add(new AnAction("Refresh", "Refresh", AllIcons.Actions.Refresh) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            syncUpdatePatchFileAndScheduleReloadIfNeeded(null);
          }
        });
      }

      final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("APPLY_PATCH", group, true);
      gb.fill = GridBagConstraints.HORIZONTAL;
      treePanel.add(toolbar.getComponent(), gb);

      ++gb.gridy;
      gb.weighty = 1;
      gb.fill = GridBagConstraints.BOTH;
      JPanel changeTreePanel = JBUI.Panels.simplePanel(myChangesTreeList).addToTop(myErrorNotificationPanel);
      treePanel.add(ScrollPaneFactory.createScrollPane(changeTreePanel), gb);

      ++gb.gridy;
      gb.weighty = 0;
      gb.fill = GridBagConstraints.NONE;
      gb.insets.bottom = UIUtil.DEFAULT_VGAP;
      treePanel.add(myCommitLegendPanel.getComponent(), gb);

      ++gb.gridy;
      Splitter splitter = new Splitter(true, 0.7f);
      splitter.setFirstComponent(treePanel);
      splitter.setSecondComponent(myChangeListChooser);
      ++centralGb.gridy;
      centralGb.weighty = 1;
      centralGb.fill = GridBagConstraints.BOTH;
      myCenterPanel.add(splitter, centralGb);
    }
    return myCenterPanel;
  }

  @NotNull
  private static GridBagConstraints createConstraints() {
    return new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insets(1), 0, 0);
  }

  private void paintBusy(final boolean requestPut) {
    if (requestPut) {
      myChangesTreeList.setPaintBusy(true);
    }
    else {
      myChangesTreeList.setPaintBusy(!myLoadQueue.isEmpty());
    }
  }

  private class MyChangeTreeList extends ChangesTreeImpl<AbstractFilePatchInProgress.PatchChange> {
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
    protected DefaultTreeModel buildTreeModel(@NotNull List<AbstractFilePatchInProgress.PatchChange> changes) {
      return TreeModelBuilder.buildFromChanges(myProject, isShowFlatten(), changes, myChangeNodeDecorator);
    }

    @Override
    protected boolean isNodeEnabled(ChangesBrowserNode<?> node) {
      boolean enabled = super.isNodeEnabled(node);
      Object value = node.getUserObject();
      if (value instanceof AbstractFilePatchInProgress.PatchChange) {
        enabled &= ((AbstractFilePatchInProgress.PatchChange)value).isValid();
      }
      return enabled;
    }

    @NotNull
    private List<AbstractFilePatchInProgress.PatchChange> getOnlyValidChanges(@NotNull Collection<AbstractFilePatchInProgress.PatchChange> changes) {
      return ContainerUtil.filter(changes, AbstractFilePatchInProgress.PatchChange::isValid);
    }

    @Override
    protected void toggleChanges(Collection<?> changes) {
      List<AbstractFilePatchInProgress.PatchChange> patchChanges =
        ContainerUtil.findAll(changes, AbstractFilePatchInProgress.PatchChange.class);

      if (patchChanges.size() == 1 && !patchChanges.get(0).isValid()) {
        handleInvalidChangesAndToggle();
      }
      else {
        super.toggleChanges(getOnlyValidChanges(patchChanges));
      }
    }

    private void handleInvalidChangesAndToggle() {
      new NewBaseSelector(false).run();
      super.toggleChanges(getOnlyValidChanges(getSelectedChanges()));
    }
  }

  private class MapDirectory extends AnAction {
    private final NewBaseSelector myNewBaseSelector;

    private MapDirectory() {
      super("Map base directory", "Map base directory", AllIcons.Vcs.MapBase);
      myNewBaseSelector = new NewBaseSelector();
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
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
          final MapPopup step = new MapPopup(autoBases, myNewBaseSelector);
          JBPopupFactory.getInstance().createListPopup(step).showCenteredInCurrentWindow(myProject);
        }
      }
    }

    @Override
    public void update(AnActionEvent e) {
      final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      e.getPresentation().setEnabled((selectedChanges.size() >= 1) && (sameBase(selectedChanges)));
    }
  }

  private static boolean sameBase(final List<AbstractFilePatchInProgress.PatchChange> selectedChanges) {
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
                                                                          List<AbstractFilePatchInProgress.PatchChange> changes) {
    final NamedLegendStatuses totalNameStatuses = new NamedLegendStatuses();
    final NamedLegendStatuses includedNameStatuses = new NamedLegendStatuses();

    final Collection<AbstractFilePatchInProgress.PatchChange> included = new LinkedList<>();
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

    public NewBaseSelector() {
      this(true);
    }

    public NewBaseSelector(boolean directorySelector) {
      myDirectorySelector = directorySelector;
    }

    @Override
    public void run() {
      final FileChooserDescriptor descriptor = myDirectorySelector
                                               ? FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                               : FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
      descriptor.setTitle(String.format("Select %s Base", myDirectorySelector ? "Directory" : "File"));
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

  private static List<AbstractFilePatchInProgress> changes2patches(final List<AbstractFilePatchInProgress.PatchChange> selectedChanges) {
    return map(selectedChanges, AbstractFilePatchInProgress.PatchChange::getPatchInProgress);
  }

  private class MapPopup extends BaseListPopupStep<VirtualFile> {
    private final Runnable myNewBaseSelector;

    private MapPopup(final @NotNull List<? extends VirtualFile> aValues, Runnable newBaseSelector) {
      super("Select base directory for a path", aValues);
      myNewBaseSelector = newBaseSelector;
    }

    @Override
    public boolean isSpeedSearchEnabled() {
      return true;
    }

    @Override
    public PopupStep onChosen(final VirtualFile selectedValue, boolean finalChoice) {
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
      return value == null ? "Select base for a path" : value.getPath();
    }
  }

  private static class NamedLegendStatuses {
    private int myAdded;
    private int myModified;
    private int myDeleted;
    private int myInapplicable;

    public NamedLegendStatuses() {
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

  private static class ChangesLegendCalculator implements CommitLegendPanel.InfoCalculator {
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

  private class MyChangeNodeDecorator implements ChangeNodeDecorator {
    @Override
    public void decorate(Change change, SimpleColoredComponent component, boolean isShowFlatten) {
      if (change instanceof AbstractFilePatchInProgress.PatchChange) {
        final AbstractFilePatchInProgress.PatchChange patchChange = (AbstractFilePatchInProgress.PatchChange)change;
        final AbstractFilePatchInProgress patchInProgress = patchChange.getPatchInProgress();
        if (patchInProgress.getCurrentStrip() > 0) {
          component.append(" stripped " + patchInProgress.getCurrentStrip(), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        }
        final String text;
        if (FilePatchStatus.ADDED.equals(patchInProgress.getStatus())) {
          text = "Added";
        }
        else if (FilePatchStatus.DELETED.equals(patchInProgress.getStatus())) {
          text = "Deleted";
        }
        else {
          text = "Modified";
        }
        component.append("   ");
        component.append(text, SimpleTextAttributes.GRAY_ATTRIBUTES);
        if (!patchInProgress.baseExistsOrAdded()) {
          component.append("  ");
          component.append("Select missing base", new SimpleTextAttributes(STYLE_PLAIN, UI.getColor("link.foreground")),
                           (Runnable)myChangesTreeList::handleInvalidChangesAndToggle);
        }
        else {
          if (!patchInProgress.getStatus().equals(FilePatchStatus.ADDED) && basePathWasChanged(patchInProgress)) {
            component.append("  ");
            component.append("New base detected", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
            component.setToolTipText(
              String.format("was: %s (base dir: %s)", patchInProgress.getOriginalBeforePath(), myProject.getBasePath()) + "<br/>" +
              String.format("now: %s (base dir: %s)", patchInProgress.getPatch().getBeforeName(),
                            patchInProgress.getBase().getPath()));
          }
        }
      }
    }

    @Override
    public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
    }
  }

  private boolean basePathWasChanged(@NotNull AbstractFilePatchInProgress patchInProgress) {
    return !FileUtil
      .filesEqual(patchInProgress.myIoCurrentBase, new File(myProject.getBasePath(), patchInProgress.getOriginalBeforePath()));
  }

  private Collection<AbstractFilePatchInProgress> getIncluded() {
    return map(myChangesTreeList.getIncludedChanges(), AbstractFilePatchInProgress.PatchChange::getPatchInProgress);
  }

  @Nullable
  private LocalChangeList getSelectedChangeList() {
    return myChangeListChooser.getSelectedList(myProject);
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    runExecutor(myCallback);
  }

  private class ZeroStrip extends AnAction {
    private ZeroStrip() {
      super("Remove Directories", "Remove Directories", AllIcons.Vcs.StripNull);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      for (AbstractFilePatchInProgress.PatchChange change : selectedChanges) {
        change.getPatchInProgress().setZero();
      }
      updateTree(false);
    }
  }

  private class StripDown extends AnAction {
    private StripDown() {
      super("Restore Directory", "Restore Directory", AllIcons.Vcs.StripDown);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(isEnabled());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (!isEnabled()) return;
      final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      for (AbstractFilePatchInProgress.PatchChange change : selectedChanges) {
        change.getPatchInProgress().down();
      }
      updateTree(false);
    }

    private boolean isEnabled() {
      final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      if (selectedChanges.isEmpty()) return false;
      for (AbstractFilePatchInProgress.PatchChange change : selectedChanges) {
        if (!change.getPatchInProgress().canDown()) return false;
      }
      return true;
    }
  }

  private class StripUp extends AnAction {
    private StripUp() {
      super("Strip Directory", "Strip Directory", AllIcons.Vcs.StripUp);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(isEnabled());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (!isEnabled()) return;
      final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      for (AbstractFilePatchInProgress.PatchChange change : selectedChanges) {
        change.getPatchInProgress().up();
      }
      updateTree(false);
    }

    private boolean isEnabled() {
      final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      if (selectedChanges.isEmpty()) return false;
      for (AbstractFilePatchInProgress.PatchChange change : selectedChanges) {
        if (!change.getPatchInProgress().canUp()) return false;
      }
      return true;
    }
  }

  private class ResetStrip extends AnAction {
    private ResetStrip() {
      super("Reset Directories", "Reset Directories", AllIcons.Vcs.ResetStrip);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      for (AbstractFilePatchInProgress.PatchChange change : selectedChanges) {
        change.getPatchInProgress().reset();
      }
      updateTree(false);
    }
  }

  private class MyShowDiff extends AnAction {
    private final MyChangeComparator myMyChangeComparator;

    private MyShowDiff() {
      super("Show Diff", "Show Diff", AllIcons.Actions.Diff);
      myMyChangeComparator = new MyChangeComparator();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled((!myPatches.isEmpty()) && myContainBasedChanges);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      showDiff();
    }

    private void showDiff() {
      if (ChangeListManager.getInstance(myProject).isFreezedWithNotification(null)) return;
      if (myPatches.isEmpty() || (!myContainBasedChanges)) return;
      final List<AbstractFilePatchInProgress.PatchChange> changes = getAllChanges();
      Collections.sort(changes, myMyChangeComparator);
      List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();

      if (changes.isEmpty()) return;
      final AbstractFilePatchInProgress.PatchChange selectedChange = !selectedChanges.isEmpty() ? selectedChanges.get(0) : changes.get(0);

      int selectedIdx = 0;
      final List<DiffRequestProducer> diffRequestPresentableList = new ArrayList<>(changes.size());
      for (AbstractFilePatchInProgress.PatchChange change : changes) {
        final AbstractFilePatchInProgress patchInProgress = change.getPatchInProgress();
        if (!patchInProgress.baseExistsOrAdded()) {
          diffRequestPresentableList.add(createBaseNotFoundErrorRequest(patchInProgress));
        }
        else {
          diffRequestPresentableList.add(patchInProgress.getDiffRequestProducers(myProject, myReader));
        }
        if (change.equals(selectedChange)) {
          selectedIdx = diffRequestPresentableList.size() - 1;
        }
      }
      MyDiffRequestChain chain = new MyDiffRequestChain(diffRequestPresentableList, changes, selectedIdx);
      DiffManager.getInstance().showDiff(myProject, chain, DiffDialogHints.DEFAULT);
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
        throw new DiffRequestProducerException("Cannot find base for '" + (beforePath != null ? beforePath : afterPath) + "'");
      }
    };
  }

  private static class MyDiffRequestChain extends DiffRequestChainBase implements GoToChangePopupBuilder.Chain {
    private final List<DiffRequestProducer> myRequests;
    private final List<? extends Change> myChanges;

    public MyDiffRequestChain(@NotNull List<DiffRequestProducer> requests, @NotNull List<? extends Change> changes, int index) {
      super(index);
      assert requests.size() == changes.size();
      myRequests = requests;
      myChanges = changes;
    }

    @NotNull
    @Override
    public List<? extends DiffRequestProducer> getRequests() {
      return myRequests;
    }

    @NotNull
    @Override
    public AnAction createGoToChangeAction(@NotNull Consumer<Integer> onSelected) {
      return new ChangeGoToChangePopupAction<MyDiffRequestChain>(this, getIndex()) {
        @NotNull
        @Override
        protected DefaultTreeModel buildTreeModel(@NotNull Project project, boolean showFlatten) {
          List<TreeModelBuilder.GenericNodeData> nodesData = new ArrayList<>();
          for (int i = 0; i < myChanges.size(); i++) {
            Change change = myChanges.get(i);
            FilePath filePath = ChangesUtil.getFilePath(change);
            FileStatus fileStatus = change.getFileStatus();
            nodesData.add(new TreeModelBuilder.GenericNodeData(filePath, fileStatus, i));
          }

          TreeModelBuilder builder = new TreeModelBuilder(project, showFlatten);
          builder.setGenericNodes(nodesData, null);
          return builder.build();
        }

        @Override
        protected void onSelected(@Nullable Object object) {
          onSelected.consume((Integer)object);
        }
      };
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
