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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.ZipperUpdater;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.Icons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ApplyPatchDifferentiatedDialog extends DialogWrapper {
  private final ZipperUpdater myLoadQueue;
  private TextFieldWithBrowseButton myPatchFile;

  private final List<FilePatchInProgress> myPatches;
  private final MyChangeTreeList myChangesTreeList;

  private JComponent myCenterPanel;
  private JComponent mySouthPanel;
  private final Project myProject;

  private AtomicReference<FilePresentation> myRecentPathFileChange;
  private ApplyPatchDifferentiatedDialog.MyUpdater myUpdater;
  private Runnable myReset;
  private ChangeListChooserPanel myChangeListChooser;
  private ChangesLegendCalculator myInfoCalculator;
  private CommitLegendPanel myCommitLegendPanel;
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

    myChangeListChooser = new ChangeListChooserPanel(null, new Consumer<Boolean>() {
      public void consume(final Boolean aBoolean) {
        setOKActionEnabled(aBoolean);
      }
    });
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    myChangeListChooser.setChangeLists(changeListManager.getChangeListsCopy());
    myChangeListChooser.setDefaultSelection(changeListManager.getDefaultChangeList());
    myChangeListChooser.init(project);

    myInfoCalculator = new ChangesLegendCalculator();
    myCommitLegendPanel = new CommitLegendPanel(myInfoCalculator);

    init();
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "vcs.ApplyPatchDifferentiatedDialog";
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
      final List<FilePatchInProgress> matchedPathes = autoMatch(patches);

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

  private List<FilePatchInProgress> autoMatch(final List<TextFilePatch> list) {
    final VirtualFile baseDir = myProject.getBaseDir();

    final List<FilePatchInProgress> result = new ArrayList<FilePatchInProgress>(list.size());
    final GlobalSearchScope scope = ProjectScope.getProjectScope(myProject);
    final List<TextFilePatch> creations = new LinkedList<TextFilePatch>();
    final MultiMap<String, VirtualFile> foldersDecisions = new MultiMap<String, VirtualFile>() {
      @Override
      protected Collection<VirtualFile> createCollection() {
        return new HashSet<VirtualFile>();
      }
      @Override
      protected Collection<VirtualFile> createEmptyCollection() {
        return Collections.emptySet();
      }
    };

    for (TextFilePatch patch : list) {
      if (patch.isNewFile() || (patch.getBeforeName() == null)) {
        creations.add(patch);
        continue;
      }
      final String fileName = patch.getBeforeFileName();
      final Collection<VirtualFile> variants = filterVariants(patch, FilenameIndex.getVirtualFilesByName(myProject, fileName, scope));

      final FilePatchInProgress filePatchInProgress = new FilePatchInProgress(patch, variants, baseDir);
      result.add(filePatchInProgress);
      final String path = extractPathWithoutName(patch.getBeforeName());
      if (path != null) {
        foldersDecisions.putValue(path, filePatchInProgress.getBase());
      }
    }
    // then try to match creations
    for (TextFilePatch creation : creations) {
      final String newFileParentPath = extractPathWithoutName(creation.getAfterName());
      if (newFileParentPath == null) {
        result.add(new FilePatchInProgress(creation, null, baseDir));
      } else {
        final Collection<VirtualFile> variants = filterVariants(creation, foldersDecisions.get(newFileParentPath));
        result.add(new FilePatchInProgress(creation, variants, baseDir));
      }
    }

    return result;
  }

  private Collection<VirtualFile> filterVariants(final TextFilePatch patch, final Collection<VirtualFile> in) {
    String path = patch.getBeforeName() == null ? patch.getAfterName() : patch.getBeforeName();
    path = path.replace("\\", "/");

    final boolean caseSensitive = SystemInfo.isFileSystemCaseSensitive;
    final Collection<VirtualFile> result = new LinkedList<VirtualFile>();
    for (VirtualFile vf : in) {
      final String vfPath = vf.getPath();
      if ((caseSensitive && vfPath.endsWith(path)) || ((! caseSensitive) && StringUtil.endsWithIgnoreCase(vfPath, path))) {
        result.add(vf);
      }
    }
    return result;
  }

  @Nullable
  private String extractPathWithoutName(final String path) {
    final String replaced = path.replace("\\", "/");
    final int idx = replaced.lastIndexOf('/');
    if (idx == -1) return null;
    return replaced.substring(0, idx);
  }

  private List<TextFilePatch> loadPatches(final VirtualFile patchFile) {
    if (! patchFile.isValid()) {
      //todo
      //queueUpdateStatus("Cannot find patch file");
      return Collections.emptyList();
    }
    PatchReader reader;
    try {
      reader = new PatchReader(patchFile);
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
      diffAction.registerCustomShortcutSet(CommonShortcuts.getDiff(), myCenterPanel);
      group.add(diffAction);
      
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
    final List<FilePatchInProgress.PatchChange> changes = getAllChanges();
    final Collection<FilePatchInProgress.PatchChange> included = getIncluded(doInitCheck, changes);
    myChangesTreeList.setChangesToDisplay(changes);
    myChangesTreeList.setIncludedChanges(included);
    myChangesTreeList.repaint();

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
      final Set<Pair<String, String>> toBeIncluded = new HashSet<Pair<String, String>>();
      for (FilePatchInProgress.PatchChange change : includedNow) {
        final FilePatchInProgress patch = change.getPatchInProgress();
        toBeIncluded.add(new Pair<String, String>(patch.getPatch().getBeforeName(), patch.getPatch().getAfterName()));
      }
      for (FilePatchInProgress.PatchChange change : changes) {
        final FilePatchInProgress patch = change.getPatchInProgress();
        final Pair<String, String> pair = new Pair<String, String>(patch.getPatch().getBeforeName(), patch.getPatch().getAfterName());
        acceptChange(totalTrinity, change);
        if (toBeIncluded.contains(pair) && patch.baseExistsOrAdded()) {
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
    public void decorate(Change change, SimpleColoredComponent component) {
      if (change instanceof FilePatchInProgress.PatchChange) {
        final FilePatchInProgress.PatchChange patchChange = (FilePatchInProgress.PatchChange) change;
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

  private class MyShowDiff extends AnAction {
    private MyShowDiff() {
      super("Show Diff", "Show Diff", IconLoader.getIcon("/actions/diff.png"));
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled((! myPatches.isEmpty()) && myContainBasedChanges);
    }

    public void actionPerformed(AnActionEvent e) {
      if (myPatches.isEmpty() || (! myContainBasedChanges)) return;
      final List<FilePatchInProgress.PatchChange> changes = getAllChanges();
      final List<FilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
      int idx = 0;
      boolean goodChange = false;
      if (! selectedChanges.isEmpty()) {
        final FilePatchInProgress.PatchChange c = selectedChanges.get(0);
        for (FilePatchInProgress.PatchChange change : changes) {
          if (! change.getPatchInProgress().baseExistsOrAdded()) continue;
          goodChange = true;
          if (change.equals(c)) {
            break;
          }
          ++ idx;
        }
      }
      if (! goodChange) return;
      idx = (idx == changes.size()) ? 0 : idx;
      ShowDiffAction.showDiffForChange(changes.toArray(new Change[changes.size()]), idx, myProject,
                                       ShowDiffAction.DiffExtendUIFactory.NONE, false);
    }
  }
}
