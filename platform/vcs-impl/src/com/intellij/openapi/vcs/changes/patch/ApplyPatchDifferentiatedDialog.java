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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.PatchVirtualFileReader;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.ZipperUpdater;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.actions.DiffRequestPresentable;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.ShowDiffUIContext;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.*;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Alarm;
import com.intellij.util.NullableConsumer;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
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

  private final List<FilePatchInProgress> myPatches;
  private final MyChangeTreeList myChangesTreeList;

  private JComponent myCenterPanel;
  private JComponent mySouthPanel;
  private final Project myProject;

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
  private CommitContext myCommitContext;
  private VirtualFileAdapter myListener;
  private boolean myCanChangePatchFile;
  private String myHelpId = "reference.dialogs.vcs.patch.apply";

  public ApplyPatchDifferentiatedDialog(final Project project, final ApplyPatchExecutor callback, final List<ApplyPatchExecutor> executors,
                                        @NotNull final ApplyPatchMode applyPatchMode, @NotNull final VirtualFile patchFile) {
    this(project, callback, executors, applyPatchMode, patchFile, null, null);
  }

  public ApplyPatchDifferentiatedDialog(final Project project, final ApplyPatchExecutor callback, final List<ApplyPatchExecutor> executors,
      @NotNull final ApplyPatchMode applyPatchMode, @NotNull final List<TextFilePatch> patches, @Nullable final LocalChangeList defaultList) {
    this(project, callback, executors, applyPatchMode, null, patches, defaultList);
  }

  private ApplyPatchDifferentiatedDialog(final Project project, final ApplyPatchExecutor callback, final List<ApplyPatchExecutor> executors,
      @NotNull final ApplyPatchMode applyPatchMode, @Nullable final VirtualFile patchFile, @Nullable final List<TextFilePatch> patches,
      @Nullable final LocalChangeList defaultList) {
    super(project, true);
    myCallback = callback;
    myExecutors = executors;
    setModal(false);
    setTitle(applyPatchMode.getTitle());

    final FileChooserDescriptor descriptor = createSelectPatchDescriptor();
    descriptor.setTitle(VcsBundle.message("patch.apply.select.title"));

    myProject = project;
    myPatches = new LinkedList<FilePatchInProgress>();
    myRecentPathFileChange = new AtomicReference<FilePresentation>();
    myChangesTreeList = new MyChangeTreeList(project, Collections.<FilePatchInProgress.PatchChange>emptyList(),
      new Runnable() {
        public void run() {
          final NamedTrinity includedTrinity = new NamedTrinity();
          final Collection<FilePatchInProgress.PatchChange> includedChanges = myChangesTreeList.getIncludedChanges();
          final Set<Pair<String, String>> set = new HashSet<Pair<String, String>>();
          for (FilePatchInProgress.PatchChange change : includedChanges) {
            final TextFilePatch patch = change.getPatchInProgress().getPatch();
            final Pair<String, String> pair = new Pair<String, String>(patch.getBeforeName(), patch.getAfterName());
            if (set.contains(pair)) continue;
            set.add(pair);
            acceptChange(includedTrinity, change);
          }
          myInfoCalculator.setIncluded(includedTrinity);
          myCommitLegendPanel.update();
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
    myChangeListChooser.setDefaultSelection(changeListManager.getDefaultChangeList());
    myChangeListChooser.init();

    myInfoCalculator = new ChangesLegendCalculator();
    myCommitLegendPanel = new CommitLegendPanel(myInfoCalculator);

    init();

    if (patchFile != null && patchFile.isValid()) {
      init(patchFile);
    } else if (patches != null) {
      init(patches, defaultList);
    }

    myPatchFileLabel.setVisible(myCanChangePatchFile);
    myPatchFile.setVisible(myCanChangePatchFile);

    if (myCanChangePatchFile) {
      myListener = new VirtualFileAdapter() {
        @Override
        public void contentsChanged(VirtualFileEvent event) {
          if (myRecentPathFileChange.get() != null && myRecentPathFileChange.get().getVf() != null &&
              myRecentPathFileChange.get().getVf().equals(event.getFile())) {
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
  }

  private void queueRequest() {
    paintBusy(true);
    myLoadQueue.queue(myUpdater);
  }

  private void init(List<TextFilePatch> patches, final LocalChangeList localChangeList) {
    final List<FilePatchInProgress> matchedPathes = new MatchPatchPaths(myProject).execute(patches);

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (localChangeList != null) {
          myChangeListChooser.setDefaultSelection(localChangeList);
        }

        myPatches.clear();
        myPatches.addAll(matchedPathes);
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

  private void runExecutor(ApplyPatchExecutor executor) {
    final Collection<FilePatchInProgress> included = getIncluded();
    if (included.isEmpty()) return;
    final MultiMap<VirtualFile, FilePatchInProgress> patchGroups = new MultiMap<VirtualFile, FilePatchInProgress>();
    for (FilePatchInProgress patchInProgress : included) {
      patchGroups.putValue(patchInProgress.getBase(), patchInProgress);
    }
    final LocalChangeList selected = getSelectedChangeList();
    executor.apply(patchGroups, selected, myRecentPathFileChange.get() == null ? null : myRecentPathFileChange.get().getVf().getName(),
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

  public void init(final VirtualFile patchFile) {
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
        SwingUtilities.invokeLater(myReset);
        return;
      }
      final VirtualFile file = filePresentation.getVf();

      final PatchReader patchReader = loadPatches(filePresentation);
      if (patchReader == null) return;

      final List<FilePatchInProgress> matchedPathes = patchReader == null ? Collections.<FilePatchInProgress>emptyList() :
                                                      new MatchPatchPaths(myProject).execute(patchReader.getPatches());

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myChangeListChooser.setDefaultName(file.getNameWithoutExtension().replace('_', ' ').trim());
          myPatches.clear();
          myPatches.addAll(matchedPathes);
          myReader = patchReader;
          updateTree(true);
          paintBusy(false);
        }
      });
    }
  }

  @Nullable
  private PatchReader loadPatches(final FilePresentation filePresentation) {
    final VirtualFile patchFile = filePresentation.getVf();
    patchFile.refresh(false, false);
    if (! patchFile.isValid()) {
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
      return (file != null) && (! file.isDirectory()) ? file : null;
    }
  }

  public void reset() {
    myPatches.clear();
    myChangesTreeList.setChangesToDisplay(Collections.<FilePatchInProgress.PatchChange>emptyList());
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
      ++ gb.gridy;
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
      ++ gb.gridy;
      gb.fill = GridBagConstraints.HORIZONTAL;
      myCenterPanel.add(toolbar.getComponent(), gb);

      ++ gb.gridy;
      gb.weighty = 1;
      gb.fill = GridBagConstraints.BOTH;
      myCenterPanel.add(myChangesTreeList, gb);

      ++ gb.gridy;
      gb.weighty = 0;
      gb.fill = GridBagConstraints.NONE;
      gb.insets.bottom = UIUtil.DEFAULT_VGAP;
      myCenterPanel.add(myCommitLegendPanel.getComponent(), gb);

      ++ gb.gridy;
      gb.fill = GridBagConstraints.HORIZONTAL;
      myCenterPanel.add(myChangeListChooser, gb);
    }
    return myCenterPanel;
  }

  private void paintBusy(final boolean requestPut) {
    if (requestPut) {
      myChangesTreeList.setPaintBusy(true);
    } else {
      myChangesTreeList.setPaintBusy(! myLoadQueue.isEmpty());
    }
  }

  private static class MyChangeTreeList extends ChangesTreeList<FilePatchInProgress.PatchChange> {
    private MyChangeTreeList(Project project,
                             Collection<FilePatchInProgress.PatchChange> initiallyIncluded,
                             @Nullable Runnable inclusionListener,
                             @Nullable ChangeNodeDecorator decorator) {
      super(project, initiallyIncluded, true, false, inclusionListener, decorator);
    }

    @Override
    protected DefaultTreeModel buildTreeModel(List<FilePatchInProgress.PatchChange> changes, ChangeNodeDecorator changeNodeDecorator) {
      TreeModelBuilder builder = new TreeModelBuilder(myProject, false);
      return builder.buildModel(ObjectsConvertor.convert(changes,
                                                         new Convertor<FilePatchInProgress.PatchChange, Change>() {
                                                           public Change convert(FilePatchInProgress.PatchChange o) {
                                                             return o;
                                                           }
                                                         }), changeNodeDecorator);
    }

    @Override
    protected List<FilePatchInProgress.PatchChange> getSelectedObjects(ChangesBrowserNode<FilePatchInProgress.PatchChange> node) {
      final List<Change> under = node.getAllChangesUnder();
      return ObjectsConvertor.convert(under, new Convertor<Change, FilePatchInProgress.PatchChange>() {
        public FilePatchInProgress.PatchChange convert(Change o) {
          return (FilePatchInProgress.PatchChange) o;
        }
      });
    }

    @Override
    protected FilePatchInProgress.PatchChange getLeadSelectedObject(ChangesBrowserNode node) {
      final Object o = node.getUserObject();
      if (o instanceof FilePatchInProgress.PatchChange) {
        return (FilePatchInProgress.PatchChange) o;
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
      final List<FilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      if ((selectedChanges.size() >= 1) && (sameBase(selectedChanges))) {
        final FilePatchInProgress.PatchChange patchChange = selectedChanges.get(0);
        final FilePatchInProgress patch = patchChange.getPatchInProgress();
        final List<VirtualFile> autoBases = patch.getAutoBasesCopy();
        if (autoBases.isEmpty() || (autoBases.size() == 1 && autoBases.get(0).equals(patch.getBase()))) {
          myNewBaseSelector.run();
        } else {
          autoBases.add(null);
          final MapPopup step = new MapPopup(autoBases, myNewBaseSelector);
          JBPopupFactory.getInstance().createListPopup(step).showCenteredInCurrentWindow(myProject);
        }
      }
    }

    @Override
    public void update(AnActionEvent e) {
      final List<FilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      e.getPresentation().setEnabled((selectedChanges.size() >= 1) && (sameBase(selectedChanges)));
    }
  }

  private boolean sameBase(final List<FilePatchInProgress.PatchChange> selectedChanges) {
    VirtualFile base = null;
    for (FilePatchInProgress.PatchChange change : selectedChanges) {
      final VirtualFile changeBase = change.getPatchInProgress().getBase();
      if (base == null) {
        base = changeBase;
      } else if (! base.equals(changeBase)) {
        return false;
      }
    }
    return true;
  }

  private void updateTree(boolean doInitCheck) {
    final List<FilePatchInProgress> patchesToSelect = changes2patches(myChangesTreeList.getSelectedChanges());
    final List<FilePatchInProgress.PatchChange> changes = getAllChanges();
    final Collection<FilePatchInProgress.PatchChange> included = getIncluded(doInitCheck, changes);

    myChangesTreeList.setChangesToDisplay(changes);
    myChangesTreeList.setIncludedChanges(included);
    if (doInitCheck) {
      myChangesTreeList.expandAll();
    }
    myChangesTreeList.repaint();
    if ((! doInitCheck) && patchesToSelect != null) {
      final List<FilePatchInProgress.PatchChange> toSelect = new ArrayList<FilePatchInProgress.PatchChange>(patchesToSelect.size());
      for (FilePatchInProgress.PatchChange change : changes) {
        if (patchesToSelect.contains(change.getPatchInProgress())) {
          toSelect.add(change);
        }
      }
      myChangesTreeList.select(toSelect);
    }

    myContainBasedChanges = false;
    for (FilePatchInProgress patch : myPatches) {
      if (patch.baseExistsOrAdded()) {
        myContainBasedChanges = true;
        break;
      }
    }
  }

  private List<FilePatchInProgress.PatchChange> getAllChanges() {
    return ObjectsConvertor.convert(myPatches,
                                                                                   new Convertor<FilePatchInProgress, FilePatchInProgress.PatchChange>() {
                                                                                     public FilePatchInProgress.PatchChange convert(FilePatchInProgress o) {
                                                                                       return o.getChange();
                                                                                     }
                                                                                   });
  }

  private void acceptChange(final NamedTrinity trinity, final FilePatchInProgress.PatchChange change) {
    final FilePatchInProgress patchInProgress = change.getPatchInProgress();
    if (FilePatchStatus.ADDED.equals(patchInProgress.getStatus())) {
      trinity.plusAdded();
    } else if (FilePatchStatus.DELETED.equals(patchInProgress.getStatus())) {
      trinity.plusDeleted();
    } else {
      trinity.plusModified();
    }
  }

  private Collection<FilePatchInProgress.PatchChange> getIncluded(boolean doInitCheck, List<FilePatchInProgress.PatchChange> changes) {
    final NamedTrinity totalTrinity = new NamedTrinity();
    final NamedTrinity includedTrinity = new NamedTrinity();

    final Collection<FilePatchInProgress.PatchChange> included = new LinkedList<FilePatchInProgress.PatchChange>();
    if (doInitCheck) {
      for (FilePatchInProgress.PatchChange change : changes) {
        acceptChange(totalTrinity, change);
        final FilePatchInProgress filePatchInProgress = change.getPatchInProgress();
        if (filePatchInProgress.baseExistsOrAdded()) {
          acceptChange(includedTrinity, change);
          included.add(change);
        }
      }
    } else {
      // todo maybe written pretty
      final Collection<FilePatchInProgress.PatchChange> includedNow = myChangesTreeList.getIncludedChanges();
      final Set<FilePatchInProgress> toBeIncluded = new HashSet<FilePatchInProgress>();
      for (FilePatchInProgress.PatchChange change : includedNow) {
        final FilePatchInProgress patch = change.getPatchInProgress();
        toBeIncluded.add(patch);
      }
      for (FilePatchInProgress.PatchChange change : changes) {
        final FilePatchInProgress patch = change.getPatchInProgress();
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

      final List<FilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      if (selectedChanges.size() >= 1) {
        for (FilePatchInProgress.PatchChange patchChange : selectedChanges) {
          final FilePatchInProgress patch = patchChange.getPatchInProgress();
          patch.setNewBase(selectedFile);
        }
        updateTree(false);
      }
    }
  }

  private List<FilePatchInProgress> changes2patches(final List<FilePatchInProgress.PatchChange> selectedChanges) {
    return ObjectsConvertor.convert(selectedChanges, new Convertor<FilePatchInProgress.PatchChange, FilePatchInProgress>() {
      public FilePatchInProgress convert(FilePatchInProgress.PatchChange o) {
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
      final List<FilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      if (selectedChanges.size() >= 1) {
        for (FilePatchInProgress.PatchChange patchChange : selectedChanges) {
          final FilePatchInProgress patch = patchChange.getPatchInProgress();
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

    public NamedTrinity(int added, int modified, int deleted) {
      myAdded = added;
      myModified = modified;
      myDeleted = deleted;
    }

    public void plusAdded() {
      ++ myAdded;
    }

    public void plusModified() {
      ++ myModified;
    }

    public void plusDeleted() {
      ++ myDeleted;
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
      if (change instanceof FilePatchInProgress.PatchChange) {
        final FilePatchInProgress.PatchChange patchChange = (FilePatchInProgress.PatchChange) change;
        if (! isShowFlatten) {
          // add change subpath
          final TextFilePatch filePatch = patchChange.getPatchInProgress().getPatch();
          final String patchPath = filePatch.getAfterName() == null ? filePatch.getBeforeName() : filePatch.getAfterName();
          component.append("   ");
          component.append("["+ patchPath + "]", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
        if (patchChange.getPatchInProgress().getCurrentStrip() > 0) {
          component.append(" stripped " + patchChange.getPatchInProgress().getCurrentStrip(), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        }
        final String text;
        if (FilePatchStatus.ADDED.equals(patchChange.getPatchInProgress().getStatus())) {
          text = "(Added)";
        } else if (FilePatchStatus.DELETED.equals(patchChange.getPatchInProgress().getStatus())) {
          text = "(Deleted)";
        } else {
          text = "(Modified)";
        }
        component.append("   ");
        component.append(text, SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }

    public List<Pair<String, Stress>> stressPartsOfFileName(final Change change, final String parentPath) {
      if (change instanceof FilePatchInProgress.PatchChange) {
        final FilePatchInProgress.PatchChange patchChange = (FilePatchInProgress.PatchChange) change;
        final String basePath = patchChange.getPatchInProgress().getBase().getPath();
        final String basePathCorrected = basePath.trim().replace('/', File.separatorChar);
        if (parentPath.startsWith(basePathCorrected)) {
          return Arrays.asList(new Pair<String, Stress>(basePathCorrected, Stress.BOLD),
                               new Pair<String, Stress>(StringUtil.tail(parentPath, basePathCorrected.length()), Stress.PLAIN));
        }
      }
      return null;
    }

    public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
    }
  }

  public Collection<FilePatchInProgress> getIncluded() {
    return ObjectsConvertor.convert(myChangesTreeList.getIncludedChanges(),
                                    new Convertor<FilePatchInProgress.PatchChange, FilePatchInProgress>() {
                                      public FilePatchInProgress convert(FilePatchInProgress.PatchChange o) {
                                        return o.getPatchInProgress();
                                      }
                                    });
  }

  public LocalChangeList getSelectedChangeList() {
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
      final List<FilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      for (FilePatchInProgress.PatchChange change : selectedChanges) {
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
      if (! isEnabled()) return;
      final List<FilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      for (FilePatchInProgress.PatchChange change : selectedChanges) {
        change.getPatchInProgress().down();
      }
      updateTree(false);
    }

    private boolean isEnabled() {
      final List<FilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      if (selectedChanges.isEmpty()) return false;
      for (FilePatchInProgress.PatchChange change : selectedChanges) {
        if (! change.getPatchInProgress().canDown()) return false;
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
      if (! isEnabled()) return;
      final List<FilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      for (FilePatchInProgress.PatchChange change : selectedChanges) {
        change.getPatchInProgress().up();
      }
      updateTree(false);
    }

    private boolean isEnabled() {
      final List<FilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      if (selectedChanges.isEmpty()) return false;
      for (FilePatchInProgress.PatchChange change : selectedChanges) {
        if (! change.getPatchInProgress().canUp()) return false;
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
      final List<FilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      for (FilePatchInProgress.PatchChange change : selectedChanges) {
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
      e.getPresentation().setEnabled((! myPatches.isEmpty()) && myContainBasedChanges);
    }

    public void actionPerformed(AnActionEvent e) {
      showDiff();
    }

    private void showDiff() {
      if (ChangeListManager.getInstance(myProject).isFreezedWithNotification(null)) return;
      if (myPatches.isEmpty() || (! myContainBasedChanges)) return;
      final List<FilePatchInProgress.PatchChange> changes = getAllChanges();
      Collections.sort(changes, myMyChangeComparator);
      final List<FilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();

      int selectedIdx = 0;
      final ArrayList<DiffRequestPresentable> diffRequestPresentables = new ArrayList<DiffRequestPresentable>(changes.size());
      if (selectedChanges.isEmpty()) {
        selectedChanges.addAll(changes);
      }
      if (! selectedChanges.isEmpty()) {
        final FilePatchInProgress.PatchChange c = selectedChanges.get(0);
        for (FilePatchInProgress.PatchChange change : changes) {
          final FilePatchInProgress patchInProgress = change.getPatchInProgress();
          if (! patchInProgress.baseExistsOrAdded()) continue;
          final TextFilePatch patch = patchInProgress.getPatch();
          final String path = patch.getBeforeName() == null ? patch.getAfterName() : patch.getBeforeName();
          final DiffRequestPresentable diffRequestPresentable =
            change.createDiffRequestPresentable(myProject, new Getter<CharSequence>() {
              @Override
              public CharSequence get() {
                return myReader.getBaseRevision(myProject, path);
              }
            });
          if (diffRequestPresentable != null) {
            diffRequestPresentables.add(diffRequestPresentable);
          }
          if (change.equals(c)) {
            selectedIdx = diffRequestPresentables.size() - 1;
          }
        }
      }
      if (diffRequestPresentables.isEmpty()) return;
      ShowDiffAction.showDiffImpl(myProject, diffRequestPresentables, selectedIdx, new ShowDiffUIContext(false));
    }
  }

  private class MyChangeComparator implements Comparator<FilePatchInProgress.PatchChange> {
    public int compare(FilePatchInProgress.PatchChange o1, FilePatchInProgress.PatchChange o2) {
      if (PropertiesComponent.getInstance(myProject).isTrueValue("ChangesBrowser.SHOW_FLATTEN")) {
        return o1.getPatchInProgress().getIoCurrentBase().getName().compareTo(o2.getPatchInProgress().getIoCurrentBase().getName());
      }
      return o1.getPatchInProgress().getIoCurrentBase().compareTo(o2.getPatchInProgress().getIoCurrentBase());
    }
  }
}
