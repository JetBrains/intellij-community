/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeGoToChangePopupAction;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFilePatch;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.*;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.NullableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
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

public class ApplyPatchDifferentiatedDialog extends DialogWrapper {
  private final ZipperUpdater myLoadQueue;
  private final TextFieldWithBrowseButton myPatchFile;

  private final List<AbstractFilePatchInProgress> myPatches;
  private final List<ShelvedBinaryFilePatch> myBinaryShelvedPatches;
  @NotNull private final MyChangeTreeList myChangesTreeList;
  @Nullable private final Collection<Change> myPreselectedChanges;

  private JComponent myCenterPanel;
  protected final Project myProject;

  private final AtomicReference<FilePresentation> myRecentPathFileChange;
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
  private VirtualFileAdapter myListener;
  private final boolean myCanChangePatchFile;
  private String myHelpId = "reference.dialogs.vcs.patch.apply";
  private final LocalChangeList myLocalChangeList;
  private final String myCommitMessage; //may be provided externally; todo: parse with Additional Info Reader from patch meta information

  public ApplyPatchDifferentiatedDialog(final Project project, final ApplyPatchExecutor callback, final List<ApplyPatchExecutor> executors,
                                        @NotNull final ApplyPatchMode applyPatchMode, @NotNull final VirtualFile patchFile) {
    this(project, callback, executors, applyPatchMode, patchFile, null, null, null, null, null);
  }

  public ApplyPatchDifferentiatedDialog(final Project project,
                                        final ApplyPatchExecutor callback,
                                        final List<ApplyPatchExecutor> executors,
                                        @NotNull final ApplyPatchMode applyPatchMode,
                                        @NotNull final List<TextFilePatch> patches,
                                        @Nullable final LocalChangeList defaultList) {
    this(project, callback, executors, applyPatchMode, null, patches, defaultList, null, null, null);
  }

  public ApplyPatchDifferentiatedDialog(final Project project,
                                        final ApplyPatchExecutor callback,
                                        final List<ApplyPatchExecutor> executors,
                                        @NotNull final ApplyPatchMode applyPatchMode,
                                        @Nullable final VirtualFile patchFile,
                                        @Nullable final List<TextFilePatch> patches,
                                        @Nullable final LocalChangeList defaultList,
                                        @Nullable List<ShelvedBinaryFilePatch> binaryShelvedPatches,
                                        @Nullable Collection<Change> preselectedChanges, @Nullable String externalCommitMessage) {
    super(project, true);
    myCallback = callback;
    myExecutors = executors;
    setModal(false);
    setTitle(applyPatchMode.getTitle());

    final FileChooserDescriptor descriptor = createSelectPatchDescriptor();
    descriptor.setTitle(VcsBundle.message("patch.apply.select.title"));

    myProject = project;
    myPatches = new LinkedList<AbstractFilePatchInProgress>();
    myRecentPathFileChange = new AtomicReference<FilePresentation>();
    myBinaryShelvedPatches = binaryShelvedPatches;
    myPreselectedChanges = preselectedChanges;
    myChangesTreeList = new MyChangeTreeList(project, Collections.<AbstractFilePatchInProgress.PatchChange>emptyList(),
                                             new Runnable() {
                                               public void run() {
                                                 final NamedTrinity includedTrinity = new NamedTrinity();
                                                 final Collection<AbstractFilePatchInProgress.PatchChange> includedChanges =
                                                   myChangesTreeList.getIncludedChanges();
                                                 final Set<Couple<String>> set = new HashSet<Couple<String>>();
                                                 for (AbstractFilePatchInProgress.PatchChange change : includedChanges) {
                                                   final FilePatch patch = change.getPatchInProgress().getPatch();
                                                   final Couple<String> pair = Couple.of(patch.getBeforeName(), patch.getAfterName());
                                                   if (set.contains(pair)) continue;
                                                   set.add(pair);
                                                   acceptChange(includedTrinity, change);
                                                 }
                                                 myInfoCalculator.setIncluded(includedTrinity);
                                                 myCommitLegendPanel.update();
                                                 updateOkActions();
                                               }
                                             }, new MyChangeNodeDecorator());
    myChangesTreeList.setDoubleClickHandler(new Runnable() {
      @Override
      public void run() {
        new MyShowDiff().showDiff();
      }
    });

    myUpdater = new MyUpdater();
    myPatchFile = new TextFieldWithBrowseButton();
    myPatchFile.addBrowseFolderListener(VcsBundle.message("patch.apply.select.title"), "", project, descriptor);
    myPatchFile.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        setPathFileChangeDefault();
        queueRequest();
      }
    });

    myCommitMessage = externalCommitMessage;
    myLocalChangeList = defaultList;
    myLoadQueue = new ZipperUpdater(500, Alarm.ThreadToUse.POOLED_THREAD, getDisposable());
    myCanChangePatchFile = applyPatchMode.isCanChangePatchFile();
    myReset = myCanChangePatchFile ? new Runnable() {
      public void run() {
        reset();
      }
    } : EmptyRunnable.getInstance();

    myChangeListChooser = new ChangeListChooserPanel(project, new NullableConsumer<String>() {
      public void consume(final @Nullable String errorMessage) {
        setOKActionEnabled(errorMessage == null);
        setErrorText(errorMessage);
      }
    });
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    myChangeListChooser.setChangeLists(changeListManager.getChangeListsCopy());
    myChangeListChooser.setDefaultSelection(myLocalChangeList != null ? myLocalChangeList : changeListManager.getDefaultChangeList());
    myChangeListChooser.init();

    myInfoCalculator = new ChangesLegendCalculator();
    myCommitLegendPanel = new CommitLegendPanel(myInfoCalculator);

    init();

    if (patchFile != null && patchFile.isValid()) {
      init(patchFile);
    }
    else if (patches != null) {
      init(patches);
    }

    myPatchFileLabel.setVisible(myCanChangePatchFile);
    myPatchFile.setVisible(myCanChangePatchFile);

    if (myCanChangePatchFile) {
      myListener = new VirtualFileAdapter() {
        @Override
        public void contentsChanged(@NotNull VirtualFileEvent event) {
          if (myRecentPathFileChange.get() != null && myRecentPathFileChange.get().getVf() != null &&
              event.getFile().equals(myRecentPathFileChange.get().getVf())) {
            queueRequest();
          }
        }
      };
      final VirtualFileManager fileManager = VirtualFileManager.getInstance();
      fileManager.addVirtualFileListener(myListener);
      Disposer.register(getDisposable(), new Disposable() {
        @Override
        public void dispose() {
          fileManager.removeVirtualFileListener(myListener);
        }
      });
    }
    updateOkActions();
  }

  private void updateOkActions() {
    setOKActionEnabled(!myChangesTreeList.getIncludedChanges().isEmpty());
  }

  private void queueRequest() {
    paintBusy(true);
    myLoadQueue.queue(myUpdater);
  }

  private void init(List<? extends FilePatch> patches) {
    final List<AbstractFilePatchInProgress> matchedPatches = new MatchPatchPaths(myProject).execute(patches);
    //todo add shelved binary patches
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        myPatches.clear();
        myPatches.addAll(matchedPatches);
        updateTree(true);
      }
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
    final List<Action> actions = new ArrayList<Action>(4);
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
    final MultiMap<VirtualFile, AbstractFilePatchInProgress> patchGroups = new MultiMap<VirtualFile, AbstractFilePatchInProgress>();
    for (AbstractFilePatchInProgress patchInProgress : included) {
      patchGroups.putValue(patchInProgress.getBase(), patchInProgress);
    }
    final LocalChangeList selected = getSelectedChangeList();
    FilePresentation presentation = myRecentPathFileChange.get();
    VirtualFile vf = presentation != null ? presentation.getVf() : null;
    executor.apply(patchGroups, selected, vf == null ? null : vf.getName(),
                   myReader == null ? null : myReader.getAdditionalInfo(ApplyPatchDefaultExecutor.pathsFromGroups(patchGroups)));
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

  private void setPathFileChangeDefault() {
    myRecentPathFileChange.set(new FilePresentation(myPatchFile.getText()));
  }

  private void init(final VirtualFile patchFile) {
    myPatchFile.setText(patchFile.getPresentableUrl());
    myRecentPathFileChange.set(new FilePresentation(patchFile));
    queueRequest();
  }

  public void setHelpId(String s) {
    myHelpId = s;
  }

  private class MyUpdater implements Runnable {
    public void run() {
      final FilePresentation filePresentation = myRecentPathFileChange.get();
      if ((filePresentation == null) || (filePresentation.getVf() == null)) {
        ApplicationManager.getApplication().invokeLater(myReset);
        return;
      }
      final VirtualFile file = filePresentation.getVf();

      final PatchReader patchReader = loadPatches(filePresentation);
      if (patchReader == null) return;

      List<FilePatch> filePatches = ContainerUtil.<FilePatch>newArrayList(patchReader.getPatches());
      if (!ContainerUtil.isEmpty(myBinaryShelvedPatches)) {
        filePatches.addAll(myBinaryShelvedPatches);
      }
      final List<AbstractFilePatchInProgress> matchedPatches = new MatchPatchPaths(myProject).execute(filePatches);

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myChangeListChooser
            .setDefaultName(myCommitMessage != null ? myCommitMessage : file.getNameWithoutExtension().replace('_', ' ').trim());
          myPatches.clear();
          myPatches.addAll(matchedPatches);
          myReader = patchReader;
          updateTree(true);
          paintBusy(false);
        }
      });
    }
  }

  @Nullable
  private static PatchReader loadPatches(final FilePresentation filePresentation) {
    final VirtualFile patchFile = filePresentation.getVf();
    if (patchFile == null) return null;
    patchFile.refresh(false, false);
    if (!patchFile.isValid()) {
      return null;
    }

    PatchReader reader;
    try {
      reader = PatchVirtualFileReader.create(patchFile);
    }
    catch (IOException e) {
      return null;
    }
    try {
      reader.parseAllPatches();
    }
    catch (PatchSyntaxException e) {
      return null;
    }

    return reader;
  }

  private static class FilePresentation {
    private final VirtualFile myVf;
    private final String myPath;

    private FilePresentation(VirtualFile vf) {
      myVf = vf;
      myPath = null;
    }

    private FilePresentation(String path) {
      myPath = path;
      myVf = null;
    }

    @Nullable
    public VirtualFile getVf() {
      if (myVf != null) {
        return myVf;
      }
      final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(myPath);
      return (file != null) && (!file.isDirectory()) ? file : null;
    }
  }

  private void reset() {
    myPatches.clear();
    myChangesTreeList.setChangesToDisplay(Collections.<AbstractFilePatchInProgress.PatchChange>emptyList());
    myChangesTreeList.repaint();
    myContainBasedChanges = false;
    paintBusy(false);
  }

  @Override
  protected JComponent createCenterPanel() {
    if (myCenterPanel == null) {
      myCenterPanel = new JPanel(new GridBagLayout());
      final GridBagConstraints gb =
        new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(1, 1, 1, 1), 0, 0);

      myPatchFileLabel = new JLabel(VcsBundle.message("patch.apply.file.name.field"));
      myPatchFileLabel.setLabelFor(myPatchFile);
      myCenterPanel.add(myPatchFileLabel, gb);

      gb.fill = GridBagConstraints.HORIZONTAL;
      ++gb.gridy;
      myCenterPanel.add(myPatchFile, gb);

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
            queueRequest();
          }
        });
      }

      final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("APPLY_PATCH", group, true);
      ++gb.gridy;
      gb.fill = GridBagConstraints.HORIZONTAL;
      myCenterPanel.add(toolbar.getComponent(), gb);

      ++gb.gridy;
      gb.weighty = 1;
      gb.fill = GridBagConstraints.BOTH;
      myCenterPanel.add(myChangesTreeList, gb);

      ++gb.gridy;
      gb.weighty = 0;
      gb.fill = GridBagConstraints.NONE;
      gb.insets.bottom = UIUtil.DEFAULT_VGAP;
      myCenterPanel.add(myCommitLegendPanel.getComponent(), gb);

      ++gb.gridy;
      gb.fill = GridBagConstraints.HORIZONTAL;
      myCenterPanel.add(myChangeListChooser, gb);
    }
    return myCenterPanel;
  }

  private void paintBusy(final boolean requestPut) {
    if (requestPut) {
      myChangesTreeList.setPaintBusy(true);
    }
    else {
      myChangesTreeList.setPaintBusy(!myLoadQueue.isEmpty());
    }
  }

  private static class MyChangeTreeList extends ChangesTreeList<AbstractFilePatchInProgress.PatchChange> {
    private MyChangeTreeList(Project project,
                             Collection<AbstractFilePatchInProgress.PatchChange> initiallyIncluded,
                             @Nullable Runnable inclusionListener,
                             @Nullable ChangeNodeDecorator decorator) {
      super(project, initiallyIncluded, true, false, inclusionListener, decorator);
    }

    @Override
    protected DefaultTreeModel buildTreeModel(List<AbstractFilePatchInProgress.PatchChange> changes,
                                              ChangeNodeDecorator changeNodeDecorator) {
      TreeModelBuilder builder = new TreeModelBuilder(myProject, false);
      return builder.buildModel(ObjectsConvertor.convert(changes,
                                                         new Convertor<AbstractFilePatchInProgress.PatchChange, Change>() {
                                                           public Change convert(AbstractFilePatchInProgress.PatchChange o) {
                                                             return o;
                                                           }
                                                         }), changeNodeDecorator);
    }

    @Override
    protected List<AbstractFilePatchInProgress.PatchChange> getSelectedObjects(ChangesBrowserNode<AbstractFilePatchInProgress.PatchChange> node) {
      final List<Change> under = node.getAllChangesUnder();
      return ObjectsConvertor.convert(under, new Convertor<Change, AbstractFilePatchInProgress.PatchChange>() {
        public AbstractFilePatchInProgress.PatchChange convert(Change o) {
          return (AbstractFilePatchInProgress.PatchChange)o;
        }
      });
    }

    @Override
    protected AbstractFilePatchInProgress.PatchChange getLeadSelectedObject(ChangesBrowserNode node) {
      final Object o = node.getUserObject();
      if (o instanceof AbstractFilePatchInProgress.PatchChange) {
        return (AbstractFilePatchInProgress.PatchChange)o;
      }
      return null;
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

    myChangesTreeList.setChangesToDisplay(changes);
    myChangesTreeList.setIncludedChanges(included);
    if (doInitCheck) {
      myChangesTreeList.expandAll();
    }
    myChangesTreeList.repaint();
    if ((!doInitCheck) && patchesToSelect != null) {
      final List<AbstractFilePatchInProgress.PatchChange> toSelect =
        new ArrayList<AbstractFilePatchInProgress.PatchChange>(patchesToSelect.size());
      for (AbstractFilePatchInProgress.PatchChange change : changes) {
        if (patchesToSelect.contains(change.getPatchInProgress())) {
          toSelect.add(change);
        }
      }
      myChangesTreeList.select(toSelect);
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
    return ObjectsConvertor.convert(myPatches,
                                    new Convertor<AbstractFilePatchInProgress, AbstractFilePatchInProgress.PatchChange>() {
                                      public AbstractFilePatchInProgress.PatchChange convert(AbstractFilePatchInProgress o) {
                                        return o.getChange();
                                      }
                                    });
  }

  private static void acceptChange(final NamedTrinity trinity, final AbstractFilePatchInProgress.PatchChange change) {
    final AbstractFilePatchInProgress patchInProgress = change.getPatchInProgress();
    if (FilePatchStatus.ADDED.equals(patchInProgress.getStatus())) {
      trinity.plusAdded();
    }
    else if (FilePatchStatus.DELETED.equals(patchInProgress.getStatus())) {
      trinity.plusDeleted();
    }
    else {
      trinity.plusModified();
    }
  }

  private Collection<AbstractFilePatchInProgress.PatchChange> getIncluded(boolean doInitCheck,
                                                                          List<AbstractFilePatchInProgress.PatchChange> changes) {
    final NamedTrinity totalTrinity = new NamedTrinity();
    final NamedTrinity includedTrinity = new NamedTrinity();

    final Collection<AbstractFilePatchInProgress.PatchChange> included = new LinkedList<AbstractFilePatchInProgress.PatchChange>();
    if (doInitCheck) {
      for (AbstractFilePatchInProgress.PatchChange change : changes) {
        acceptChange(totalTrinity, change);
        final AbstractFilePatchInProgress abstractFilePatchInProgress = change.getPatchInProgress();
        if (abstractFilePatchInProgress.baseExistsOrAdded() && (myPreselectedChanges == null || myPreselectedChanges.contains(change))) {
          acceptChange(includedTrinity, change);
          included.add(change);
        }
      }
    }
    else {
      // todo maybe written pretty
      final Collection<AbstractFilePatchInProgress.PatchChange> includedNow = myChangesTreeList.getIncludedChanges();
      final Set<AbstractFilePatchInProgress> toBeIncluded = new HashSet<AbstractFilePatchInProgress>();
      for (AbstractFilePatchInProgress.PatchChange change : includedNow) {
        final AbstractFilePatchInProgress patch = change.getPatchInProgress();
        toBeIncluded.add(patch);
      }
      for (AbstractFilePatchInProgress.PatchChange change : changes) {
        final AbstractFilePatchInProgress patch = change.getPatchInProgress();
        acceptChange(totalTrinity, change);
        if (toBeIncluded.contains(patch) && patch.baseExistsOrAdded()) {
          acceptChange(includedTrinity, change);
          included.add(change);
        }
      }
    }
    myInfoCalculator.setTotal(totalTrinity);
    myInfoCalculator.setIncluded(includedTrinity);
    myCommitLegendPanel.update();
    return included;
  }

  private class NewBaseSelector implements Runnable {
    public void run() {
      final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      VirtualFile selectedFile = FileChooser.chooseFile(descriptor, myProject, null);
      if (selectedFile == null) {
        return;
      }

      final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      if (selectedChanges.size() >= 1) {
        for (AbstractFilePatchInProgress.PatchChange patchChange : selectedChanges) {
          final AbstractFilePatchInProgress patch = patchChange.getPatchInProgress();
          patch.setNewBase(selectedFile);
        }
        updateTree(false);
      }
    }
  }

  private static List<AbstractFilePatchInProgress> changes2patches(final List<AbstractFilePatchInProgress.PatchChange> selectedChanges) {
    return ObjectsConvertor.convert(selectedChanges, new Convertor<AbstractFilePatchInProgress.PatchChange, AbstractFilePatchInProgress>() {
      public AbstractFilePatchInProgress convert(AbstractFilePatchInProgress.PatchChange o) {
        return o.getPatchInProgress();
      }
    });
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

  private static class NamedTrinity {
    private int myAdded;
    private int myModified;
    private int myDeleted;

    public NamedTrinity() {
      myAdded = 0;
      myModified = 0;
      myDeleted = 0;
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

    public int getAdded() {
      return myAdded;
    }

    public int getModified() {
      return myModified;
    }

    public int getDeleted() {
      return myDeleted;
    }
  }

  private static class ChangesLegendCalculator implements CommitLegendPanel.InfoCalculator {
    private NamedTrinity myTotal;
    private NamedTrinity myIncluded;

    private ChangesLegendCalculator() {
      myTotal = new NamedTrinity();
      myIncluded = new NamedTrinity();
    }

    public void setTotal(final NamedTrinity trinity) {
      myTotal = trinity;
    }

    public void setIncluded(final NamedTrinity trinity) {
      myIncluded = trinity;
    }

    public int getNew() {
      return myTotal.getAdded();
    }

    public int getModified() {
      return myTotal.getModified();
    }

    public int getDeleted() {
      return myTotal.getDeleted();
    }

    public int getIncludedNew() {
      return myIncluded.getAdded();
    }

    public int getIncludedModified() {
      return myIncluded.getModified();
    }

    public int getIncludedDeleted() {
      return myIncluded.getDeleted();
    }
  }

  private static class MyChangeNodeDecorator implements ChangeNodeDecorator {
    public void decorate(Change change, SimpleColoredComponent component, boolean isShowFlatten) {
      if (change instanceof AbstractFilePatchInProgress.PatchChange) {
        final AbstractFilePatchInProgress.PatchChange patchChange = (AbstractFilePatchInProgress.PatchChange)change;
        if (!isShowFlatten) {
          // add change sub path
          final FilePatch filePatch = patchChange.getPatchInProgress().getPatch();
          final String patchPath = filePatch.getAfterName() == null ? filePatch.getBeforeName() : filePatch.getAfterName();
          component.append("   ");
          component.append("[" + patchPath + "]", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
        if (patchChange.getPatchInProgress().getCurrentStrip() > 0) {
          component.append(" stripped " + patchChange.getPatchInProgress().getCurrentStrip(), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        }
        final String text;
        if (FilePatchStatus.ADDED.equals(patchChange.getPatchInProgress().getStatus())) {
          text = "(Added)";
        }
        else if (FilePatchStatus.DELETED.equals(patchChange.getPatchInProgress().getStatus())) {
          text = "(Deleted)";
        }
        else {
          text = "(Modified)";
        }
        component.append("   ");
        component.append(text, SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }

    public List<Pair<String, Stress>> stressPartsOfFileName(final Change change, final String parentPath) {
      if (change instanceof AbstractFilePatchInProgress.PatchChange) {
        final AbstractFilePatchInProgress.PatchChange patchChange = (AbstractFilePatchInProgress.PatchChange)change;
        final String basePath = patchChange.getPatchInProgress().getBase().getPath();
        final String basePathCorrected = basePath.trim().replace('/', File.separatorChar);
        if (parentPath.startsWith(basePathCorrected)) {
          return Arrays.asList(Pair.create(basePathCorrected, Stress.BOLD),
                               Pair.create(StringUtil.tail(parentPath, basePathCorrected.length()), Stress.PLAIN));
        }
      }
      return null;
    }

    public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
    }
  }

  private Collection<AbstractFilePatchInProgress> getIncluded() {
    return ObjectsConvertor.convert(myChangesTreeList.getIncludedChanges(),
                                    new Convertor<AbstractFilePatchInProgress.PatchChange, AbstractFilePatchInProgress>() {
                                      public AbstractFilePatchInProgress convert(AbstractFilePatchInProgress.PatchChange o) {
                                        return o.getPatchInProgress();
                                      }
                                    });
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

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled((!myPatches.isEmpty()) && myContainBasedChanges);
    }

    public void actionPerformed(AnActionEvent e) {
      showDiff();
    }

    private void showDiff() {
      if (ChangeListManager.getInstance(myProject).isFreezedWithNotification(null)) return;
      if (myPatches.isEmpty() || (!myContainBasedChanges)) return;
      final List<AbstractFilePatchInProgress.PatchChange> changes = getAllChanges();
      Collections.sort(changes, myMyChangeComparator);
      List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();

      int selectedIdx = 0;
      final List<DiffRequestProducer> diffRequestPresentableList = new ArrayList<DiffRequestProducer>(changes.size());
      if (selectedChanges.isEmpty()) {
        selectedChanges = changes;
      }
      if (!selectedChanges.isEmpty()) {
        final AbstractFilePatchInProgress.PatchChange c = selectedChanges.get(0);
        for (AbstractFilePatchInProgress.PatchChange change : changes) {
          final AbstractFilePatchInProgress patchInProgress = change.getPatchInProgress();
          if (!patchInProgress.baseExistsOrAdded()) {
            diffRequestPresentableList.add(createBaseNotFoundErrorRequest(patchInProgress));
          }
          else {
            diffRequestPresentableList.add(patchInProgress.getDiffRequestProducers(myProject, myReader));
          }
          if (change.equals(c)) {
            selectedIdx = diffRequestPresentableList.size() - 1;
          }
        }
      }
      if (diffRequestPresentableList.isEmpty()) return;
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

  private static class MyDiffRequestChain extends UserDataHolderBase implements DiffRequestChain, GoToChangePopupBuilder.Chain {
    private final List<DiffRequestProducer> myRequests;
    private final List<? extends Change> myChanges;
    private int myIndex;

    public MyDiffRequestChain(@NotNull List<DiffRequestProducer> requests, @NotNull List<? extends Change> changes, int index) {
      myRequests = requests;
      myChanges = changes;

      myIndex = index >= 0 ? index : 0;
    }

    @NotNull
    @Override
    public List<? extends DiffRequestProducer> getRequests() {
      return myRequests;
    }

    @Override
    public int getIndex() {
      return myIndex;
    }

    @Override
    public void setIndex(int index) {
      assert index >= 0 && index < myRequests.size();
      myIndex = index;
    }

    @NotNull
    @Override
    public AnAction createGoToChangeAction(@NotNull Consumer<Integer> onSelected) {
      return new ChangeGoToChangePopupAction.Fake<MyDiffRequestChain>(this, myIndex, onSelected) {
        @NotNull
        @Override
        protected FilePath getFilePath(int index) {
          return ChangesUtil.getFilePath(myChanges.get(index));
        }

        @NotNull
        @Override
        protected FileStatus getFileStatus(int index) {
          return myChanges.get(index).getFileStatus();
        }
      };
    }
  }

  private class MyChangeComparator implements Comparator<AbstractFilePatchInProgress.PatchChange> {
    public int compare(AbstractFilePatchInProgress.PatchChange o1, AbstractFilePatchInProgress.PatchChange o2) {
      if (PropertiesComponent.getInstance(myProject).isTrueValue("ChangesBrowser.SHOW_FLATTEN")) {
        return o1.getPatchInProgress().getIoCurrentBase().getName().compareTo(o2.getPatchInProgress().getIoCurrentBase().getName());
      }
      return FileUtil.compareFiles(o1.getPatchInProgress().getIoCurrentBase(), o2.getPatchInProgress().getIoCurrentBase());
    }
  }
}
