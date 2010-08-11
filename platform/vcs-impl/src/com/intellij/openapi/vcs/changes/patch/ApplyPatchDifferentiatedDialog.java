/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.ZipperUpdater;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.actions.DiffRequestPresentable;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.ShowDiffUIContext;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
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
  private final Consumer<ApplyPatchDifferentiatedDialog> myCallback;

  private boolean myContainBasedChanges;

  public ApplyPatchDifferentiatedDialog(final Project project, final Consumer<ApplyPatchDifferentiatedDialog> callback) {
    super(project, true);
    myCallback = callback;
    setModal(false);
    setTitle(VcsBundle.message("patch.apply.dialog.title"));

    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return file.getFileType() == StdFileTypes.PATCH || file.getFileType() == FileTypes.PLAIN_TEXT;
      }
    };
    descriptor.setTitle(VcsBundle.message("patch.apply.select.title"));
    myUpdater = new MyUpdater();
    myPatchFile = new TextFieldWithBrowseButton();
    myPatchFile.addBrowseFolderListener(VcsBundle.message("patch.apply.select.title"), "", project, descriptor);
    myPatchFile.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        setPathFileChangeDefault();
        myLoadQueue.queue(myUpdater);
      }
    });

    myProject = project;
    myLoadQueue = new ZipperUpdater(500);
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
    myReset = new Runnable() {
      public void run() {
        reset();
      }
    };

    myChangeListChooser = new ChangeListChooserPanel(null, new Consumer<String>() {
      public void consume(final String errorMessage) {
        setOKActionEnabled(errorMessage == null);
        setErrorText(errorMessage);
      }
    });
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    myChangeListChooser.setChangeLists(changeListManager.getChangeListsCopy());
    myChangeListChooser.setDefaultSelection(changeListManager.getDefaultChangeList());
    myChangeListChooser.init(project);

    myInfoCalculator = new ChangesLegendCalculator();
    myCommitLegendPanel = new CommitLegendPanel(myInfoCalculator);

    init();
    final FileChooserDialog fileChooserDialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project);
    final VirtualFile[] files = fileChooserDialog.choose(null, project);
    if (files != null && files.length > 0) {
      init(files[0]);
    }
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "vcs.ApplyPatchDifferentiatedDialog";
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.vcs.patch.apply";
  }

  private void setPathFileChangeDefault() {
    myRecentPathFileChange.set(new FilePresentation(myPatchFile.getText()));
  }

  public void init(final VirtualFile patchFile) {
    myPatchFile.setText(patchFile.getPath());
    myRecentPathFileChange.set(new FilePresentation(patchFile));
    myLoadQueue.queue(myUpdater);
  }

  private class MyUpdater implements Runnable {
    public void run() {
      final FilePresentation filePresentation = myRecentPathFileChange.get();
      if ((filePresentation == null) || (filePresentation.getVf() == null)) {
        SwingUtilities.invokeLater(myReset);
        return;
      }
      final VirtualFile file = filePresentation.getVf();

      final List<TextFilePatch> patches = loadPatches(file);
      final AutoMatchIterator autoMatchIterator = new AutoMatchIterator(myProject);
      final List<FilePatchInProgress> matchedPathes = autoMatchIterator.execute(patches);

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myChangeListChooser.setDefaultName(file.getNameWithoutExtension().replace('_', ' ').trim());
          myPatches.clear();
          myPatches.addAll(matchedPathes);

          updateTree(true);
        }
      });
    }
  }

  private List<TextFilePatch> loadPatches(final VirtualFile patchFile) {
    if (! patchFile.isValid()) {
      //todo
      //queueUpdateStatus("Cannot find patch file");
      return Collections.emptyList();
    }
    PatchReader reader;
    try {
      reader = PatchVirtualFileReader.create(patchFile);
    }
    catch (IOException e) {
      //todo
      //queueUpdateStatus(VcsBundle.message("patch.apply.open.error", e.getMessage()));
      return Collections.emptyList();
    }
    final List<TextFilePatch> result = new LinkedList<TextFilePatch>();
    while(true) {
      FilePatch patch;
      try {
        patch = reader.readNextPatch();
      }
      catch (PatchSyntaxException e) {
        // todo
        if (e.getLine() >= 0) {
          //queueUpdateStatus(VcsBundle.message("patch.apply.load.error.line", e.getMessage(), e.getLine()));
        }
        else {
          //queueUpdateStatus(VcsBundle.message("patch.apply.load.error", e.getMessage()));
        }
        return Collections.emptyList();
      }
      if (patch == null) {
        break;
      }

      result.add((TextFilePatch) patch);
    }
    if (myPatches.isEmpty()) {
      // todo
      //queueUpdateStatus(VcsBundle.message("patch.apply.no.patches.found"));
    }
    return result;
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
  }

  @Override
  protected JComponent createCenterPanel() {
    if (myCenterPanel == null) {
      myCenterPanel = new JPanel(new GridBagLayout());
      final GridBagConstraints gb =
        new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(1, 1, 1, 1), 0, 0);

      final JLabel label = new JLabel(VcsBundle.message("create.patch.file.name.field"));
      label.setLabelFor(myPatchFile);
      myCenterPanel.add(label, gb);

      ++ gb.gridx;
      gb.fill = GridBagConstraints.HORIZONTAL;
      gb.weightx = 1;
      myCenterPanel.add(myPatchFile, gb);

      gb.gridx = 0;
      ++ gb.gridy;
      gb.weightx = 1;
      gb.weighty = 0;
      gb.fill = GridBagConstraints.HORIZONTAL;
      gb.gridwidth = 2;

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

      final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("APPLY_PATCH", group, true);
      myCenterPanel.add(toolbar.getComponent(), gb);

      gb.gridx = 0;
      ++ gb.gridy;
      gb.weighty = 1;
      gb.gridwidth = 2;
      gb.fill = GridBagConstraints.BOTH;
      myCenterPanel.add(myChangesTreeList, gb);

      final JPanel wrapper = new JPanel(new GridBagLayout());
      final GridBagConstraints gb1 =
        new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(1, 1, 1, 1), 0, 0);
      wrapper.add(myChangeListChooser, gb1);
      ++ gb1.gridx;
      gb1.fill = GridBagConstraints.NONE;
      gb1.weightx = 0;
      gb1.insets.left = 10;
      wrapper.add(myCommitLegendPanel.getComponent(), gb1);

      gb.gridx = 0;
      ++ gb.gridy;
      gb.weightx = 1;
      gb.weighty = 0;
      gb.fill = GridBagConstraints.HORIZONTAL;
      myCenterPanel.add(wrapper, gb);
    }
    return myCenterPanel;
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
      super("Map base directory", "Map base directory", IconLoader.getIcon("/vcs/mapBase.png"));
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
      final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
      VirtualFile[] selectedFiles = FileChooser.chooseFiles(myProject, descriptor);
      if (selectedFiles.length != 1 || selectedFiles[0] == null) {
        return;
      }
      final VirtualFile selectedValue = selectedFiles[0];

      final List<FilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      if (selectedChanges.size() >= 1) {
        for (FilePatchInProgress.PatchChange patchChange : selectedChanges) {
          final FilePatchInProgress patch = patchChange.getPatchInProgress();
          patch.setNewBase(selectedValue);
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
    myCallback.consume(this);
  }

  private class ZeroStrip extends AnAction {
    private ZeroStrip() {
      super("Remove Directories", "Remove Directories", IconLoader.getIcon("/vcs/stripNull.png"));
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
      super("Restore Directory", "Restore Directory", IconLoader.getIcon("/vcs/stripDown.png"));
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
      super("Strip Directory", "Strip Directory", IconLoader.getIcon("/vcs/stripUp.png"));
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
      super("Reset Directories", "Reset Directories", IconLoader.getIcon("/vcs/resetStrip.png"));
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
      super("Show Diff", "Show Diff", IconLoader.getIcon("/actions/diff.png"));
      myMyChangeComparator = new MyChangeComparator();
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled((! myPatches.isEmpty()) && myContainBasedChanges);
    }

    public void actionPerformed(AnActionEvent e) {
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
          final DiffRequestPresentable diffRequestPresentable = change.createDiffRequestPresentable(myProject);
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
