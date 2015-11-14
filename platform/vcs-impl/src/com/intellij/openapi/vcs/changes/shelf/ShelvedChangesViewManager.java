/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 23.11.2006
 * Time: 15:11:11
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.intellij.util.FontUtil.spaceAndThinSpace;

public class ShelvedChangesViewManager implements ProjectComponent {

  private static final Logger LOG = Logger.getInstance(ShelvedChangesViewManager.class);
  @NonNls static final String SHELF_CONTEXT_MENU = "Vcs.Shelf.ContextMenu";

  private final ChangesViewContentManager myContentManager;
  private final ShelveChangesManager myShelveChangesManager;
  private final Project myProject;
  private final ShelfTree myTree;
  private Content myContent = null;
  private final ShelvedChangeDeleteProvider myDeleteProvider = new ShelvedChangeDeleteProvider();
  private boolean myUpdatePending = false;
  private Runnable myPostUpdateRunnable = null;

  public static DataKey<ShelvedChangeList[]> SHELVED_CHANGELIST_KEY = DataKey.create("ShelveChangesManager.ShelvedChangeListData");
  public static DataKey<ShelvedChangeList[]> SHELVED_RECYCLED_CHANGELIST_KEY = DataKey.create("ShelveChangesManager.ShelvedRecycledChangeListData");
  public static DataKey<List<ShelvedChange>> SHELVED_CHANGE_KEY = DataKey.create("ShelveChangesManager.ShelvedChange");
  public static DataKey<List<ShelvedBinaryFile>> SHELVED_BINARY_FILE_KEY = DataKey.create("ShelveChangesManager.ShelvedBinaryFile");
  private static final Object ROOT_NODE_VALUE = new Object();
  private DefaultMutableTreeNode myRoot;
  private final Map<Couple<String>, String> myMoveRenameInfo;

  public static ShelvedChangesViewManager getInstance(Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetComponent(project, ShelvedChangesViewManager.class);
  }

  public ShelvedChangesViewManager(Project project, ChangesViewContentManager contentManager, ShelveChangesManager shelveChangesManager,
                                   final MessageBus bus) {
    myProject = project;
    myContentManager = contentManager;
    myShelveChangesManager = shelveChangesManager;
    bus.connect().subscribe(ShelveChangesManager.SHELF_TOPIC, new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myUpdatePending = true;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            updateChangesContent();
          }
        }, ModalityState.NON_MODAL);
      }
    });
    myMoveRenameInfo = new HashMap<Couple<String>, String>();

    myTree = new ShelfTree();
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setCellRenderer(new ShelfTreeCellRenderer(project, myMoveRenameInfo));
    new TreeLinkMouseListener(new ShelfTreeCellRenderer(project, myMoveRenameInfo)).installOn(myTree);

    final AnAction showDiffAction = ActionManager.getInstance().getAction("ShelvedChanges.Diff");
    showDiffAction.registerCustomShortcutSet(showDiffAction.getShortcutSet(), myTree);
    final EditSourceAction editSourceAction = new EditSourceAction();
    editSourceAction.registerCustomShortcutSet(editSourceAction.getShortcutSet(), myTree);

    PopupHandler.installPopupHandler(myTree, "ShelvedChangesPopupMenu", SHELF_CONTEXT_MENU);

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        DiffShelvedChangesAction.showShelvedChangesDiff(DataManager.getInstance().getDataContext(myTree));
        return true;
      }
    }.installOn(myTree);

    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(TreePath o) {
        final Object lc = o.getLastPathComponent();
        final Object lastComponent = lc == null ? null : ((DefaultMutableTreeNode) lc).getUserObject();
        if (lastComponent instanceof ShelvedChangeList) {
          return ((ShelvedChangeList) lastComponent).DESCRIPTION;
        } else if (lastComponent instanceof ShelvedChange) {
          final ShelvedChange shelvedChange = (ShelvedChange)lastComponent;
          return shelvedChange.getBeforeFileName() == null ? shelvedChange.getAfterFileName() : shelvedChange.getBeforeFileName();
        } else if (lastComponent instanceof ShelvedBinaryFile) {
          final ShelvedBinaryFile sbf = (ShelvedBinaryFile) lastComponent;
          final String value = sbf.BEFORE_PATH == null ? sbf.AFTER_PATH : sbf.BEFORE_PATH;
          int idx = value.lastIndexOf("/");
          idx = (idx == -1) ? value.lastIndexOf("\\") : idx;
          return idx > 0 ? value.substring(idx + 1) : value;
        }
        return null;
      }
    }, true);
  }

  public void projectOpened() {
    StartupManager startupManager = StartupManager.getInstance(myProject);
    if (startupManager == null) {
      LOG.error("Couldn't start loading shelved changes");
      return;
    }
    startupManager.registerPostStartupActivity(new Runnable() {
      @Override
      public void run() {
        updateChangesContent();
      }
    });
  }

  public void projectClosed() {
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "ShelvedChangesViewManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private void updateChangesContent() {
    myUpdatePending = false;
    final List<ShelvedChangeList> changeLists = new ArrayList<ShelvedChangeList>(myShelveChangesManager.getShelvedChangeLists());
    changeLists.addAll(myShelveChangesManager.getRecycledShelvedChangeLists());
    if (changeLists.size() == 0) {
      if (myContent != null) {
        myContentManager.removeContent(myContent);
        myContentManager.selectContent(ChangesViewContentManager.LOCAL_CHANGES);
      }
      myContent = null;
    }
    else {
      if (myContent == null) {
        JPanel rootPanel = createRootPanel();
        myContent = ContentFactory.SERVICE.getInstance().createContent(rootPanel, VcsBundle.message("shelf.tab"), false);
        myContent.setCloseable(false);
        myContentManager.addContent(myContent);
      }
      TreeState state = TreeState.createOn(myTree);
      myTree.setModel(buildChangesModel());
      state.applyTo(myTree);
      if (myPostUpdateRunnable != null) {
        myPostUpdateRunnable.run();
      }      
    }
    myPostUpdateRunnable = null;
  }

  @NotNull
  private JPanel createRootPanel() {
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree);
    pane.setBorder(null);

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.addAll((ActionGroup)ActionManager.getInstance().getAction("ShelvedChangesToolbar"));
    actionGroup.add(ActionManager.getInstance().getAction("ShelvedChangesToolbarGear"));
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, false);

    JPanel rootPanel = new JPanel(new BorderLayout());
    rootPanel.add(toolbar.getComponent(), BorderLayout.WEST);
    rootPanel.add(pane, BorderLayout.CENTER);
    DataManager.registerDataProvider(rootPanel, new TypeSafeDataProviderAdapter(myTree));

    return rootPanel;
  }

  private TreeModel buildChangesModel() {
    myRoot = new DefaultMutableTreeNode(ROOT_NODE_VALUE);   // not null for TreeState matching to work
    DefaultTreeModel model = new DefaultTreeModel(myRoot);
    final List<ShelvedChangeList> changeLists = new ArrayList<ShelvedChangeList>(myShelveChangesManager.getShelvedChangeLists());
    Collections.sort(changeLists, ChangelistComparator.getInstance());
    if (myShelveChangesManager.isShowRecycled()) {
      ArrayList<ShelvedChangeList> recycled = new ArrayList<ShelvedChangeList>(myShelveChangesManager.getRecycledShelvedChangeLists());
      changeLists.addAll(recycled);
      Collections.sort(changeLists, ChangelistComparator.getInstance());
    }
    myMoveRenameInfo.clear();

    for(ShelvedChangeList changeList: changeLists) {
      DefaultMutableTreeNode node = new DefaultMutableTreeNode(changeList);
      model.insertNodeInto(node, myRoot, myRoot.getChildCount());

      final List<Object> shelvedFilesNodes = new ArrayList<Object>();
      List<ShelvedChange> changes = changeList.getChanges(myProject);
      for(ShelvedChange change: changes) {
        putMovedMessage(change.getBeforePath(), change.getAfterPath());
        shelvedFilesNodes.add(change);
      }
      List<ShelvedBinaryFile> binaryFiles = changeList.getBinaryFiles();
      for(ShelvedBinaryFile file: binaryFiles) {
        putMovedMessage(file.BEFORE_PATH, file.AFTER_PATH);
        shelvedFilesNodes.add(file);
      }
      Collections.sort(shelvedFilesNodes, ShelvedFilePatchComparator.getInstance());
      for (int i = 0; i < shelvedFilesNodes.size(); i++) {
        final Object filesNode = shelvedFilesNodes.get(i);
        final DefaultMutableTreeNode pathNode = new DefaultMutableTreeNode(filesNode);
        model.insertNodeInto(pathNode, node, i);
      }
    }
    return model;
  }

  private static class ChangelistComparator implements Comparator<ShelvedChangeList> {
    private final static ChangelistComparator ourInstance = new ChangelistComparator();
    
    public static ChangelistComparator getInstance() {
      return ourInstance;
    }
    
    @Override
    public int compare(ShelvedChangeList o1, ShelvedChangeList o2) {
      return o2.DATE.compareTo(o1.DATE);
    }
  }

  private void putMovedMessage(final String beforeName, final String afterName) {
    final String movedMessage = RelativePathCalculator.getMovedString(beforeName, afterName);
    if (movedMessage != null) {
      myMoveRenameInfo.put(Couple.of(beforeName, afterName), movedMessage);
    }
  }

  public void activateView(final ShelvedChangeList list) {
    Runnable runnable = new Runnable() {
      public void run() {
        if (list != null) {
          TreeUtil.selectNode(myTree, TreeUtil.findNodeWithObject(myRoot, list));
        }
        myContentManager.setSelectedContent(myContent);
        ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
        if (!window.isVisible()) {
          window.activate(null);
        }
      }
    };
    if (myUpdatePending) {
      myPostUpdateRunnable = runnable;
    }
    else {
      runnable.run();
    }
  }

  private class ShelfTree extends Tree implements TypeSafeDataProvider {
    public void calcData(DataKey key, DataSink sink) {
      if (key == SHELVED_CHANGELIST_KEY) {
        final Set<ShelvedChangeList> changeLists = getSelectedLists(false);

        if (changeLists.size() > 0) {
          sink.put(SHELVED_CHANGELIST_KEY, changeLists.toArray(new ShelvedChangeList[changeLists.size()]));
        }
      }
      else if (key == SHELVED_RECYCLED_CHANGELIST_KEY) {
        final Set<ShelvedChangeList> changeLists = getSelectedLists(true);

        if (changeLists.size() > 0) {
          sink.put(SHELVED_RECYCLED_CHANGELIST_KEY, changeLists.toArray(new ShelvedChangeList[changeLists.size()]));
        }
      }
      else if (key == SHELVED_CHANGE_KEY) {
        sink.put(SHELVED_CHANGE_KEY, TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class));
      }
      else if (key == SHELVED_BINARY_FILE_KEY) {
        sink.put(SHELVED_BINARY_FILE_KEY, TreeUtil.collectSelectedObjectsOfType(this, ShelvedBinaryFile.class));
      }
      else if (key == VcsDataKeys.HAVE_SELECTED_CHANGES) {
        sink.put(VcsDataKeys.HAVE_SELECTED_CHANGES, getSelectionCount() > 0);
        /*List<ShelvedChange> shelvedChanges = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class);
        final List<ShelvedChangeList> changeLists = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChangeList.class);*/
      } else if (key == VcsDataKeys.CHANGES) {
        List<ShelvedChange> shelvedChanges = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class);
        final List<ShelvedBinaryFile> shelvedBinaryFiles = TreeUtil.collectSelectedObjectsOfType(this, ShelvedBinaryFile.class);
        if (! shelvedChanges.isEmpty() || ! shelvedBinaryFiles.isEmpty()) {
          final List<Change> changes = new ArrayList<Change>(shelvedChanges.size() + shelvedBinaryFiles.size());
          for (ShelvedChange shelvedChange : shelvedChanges) {
            changes.add(shelvedChange.getChange(myProject));
          }
          for (ShelvedBinaryFile binaryFile : shelvedBinaryFiles) {
            changes.add(binaryFile.createChange(myProject));
          }
          sink.put(VcsDataKeys.CHANGES, changes.toArray(new Change[changes.size()]));
        }
        else {
          final List<ShelvedChangeList> changeLists = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChangeList.class);
          final List<Change> changes = new ArrayList<Change>();
          for(ShelvedChangeList changeList: changeLists) {
            shelvedChanges = changeList.getChanges(myProject);
            for(ShelvedChange shelvedChange: shelvedChanges) {
              changes.add(shelvedChange.getChange(myProject));
            }
            final List<ShelvedBinaryFile> binaryFiles = changeList.getBinaryFiles();
            for (ShelvedBinaryFile file : binaryFiles) {
              changes.add(file.createChange(myProject));
            }
          }
          sink.put(VcsDataKeys.CHANGES, changes.toArray(new Change[changes.size()]));
        }
      }
      else if (key == PlatformDataKeys.DELETE_ELEMENT_PROVIDER) {
        sink.put(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, myDeleteProvider);
      } else if (CommonDataKeys.NAVIGATABLE_ARRAY.equals(key)) {
        List<ShelvedChange> shelvedChanges = new ArrayList<ShelvedChange>(TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class));
        final ArrayDeque<Navigatable> navigatables = new ArrayDeque<Navigatable>();
        final List<ShelvedChangeList> changeLists = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChangeList.class);
        for (ShelvedChangeList changeList : changeLists) {
          shelvedChanges.addAll(changeList.getChanges(myProject));
        }
        for (final ShelvedChange shelvedChange : shelvedChanges) {
          if (shelvedChange.getBeforePath() != null && ! FileStatus.ADDED.equals(shelvedChange.getFileStatus())) {
            final NavigatableAdapter navigatable = new NavigatableAdapter() {
              @Override
              public void navigate(boolean requestFocus) {
                final VirtualFile vf = shelvedChange.getBeforeVFUnderProject(myProject);
                if (vf != null) {
                  navigate(myProject, vf, true);
                }
              }
            };
            navigatables.add(navigatable);
          }
        }

        sink.put(CommonDataKeys.NAVIGATABLE_ARRAY, navigatables.toArray(new Navigatable[navigatables.size()]));
      }
    }

    private Set<ShelvedChangeList> getSelectedLists(final boolean recycled) {
      final TreePath[] selections = getSelectionPaths();
      final Set<ShelvedChangeList> changeLists = new HashSet<ShelvedChangeList>();
      if (selections != null) {
        for(TreePath path: selections) {
          if (path.getPathCount() >= 2) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getPathComponent(1);
            if (node.getUserObject() instanceof ShelvedChangeList) {
              final ShelvedChangeList list = (ShelvedChangeList)node.getUserObject();
              if (((! recycled) && (! list.isRecycled())) ||
                (recycled && list.isRecycled())) {
                changeLists.add(list);
              }
            }
          }
        }
      }
      return changeLists;
    }
  }

  private final static class ShelvedFilePatchComparator implements Comparator<Object> {
    private final static ShelvedFilePatchComparator ourInstance = new ShelvedFilePatchComparator();

    public static ShelvedFilePatchComparator getInstance() {
      return ourInstance;
    }

    public int compare(final Object o1, final Object o2) {
      final String path1 = getPath(o1);
      final String path2 = getPath(o2);
      // case-insensitive; as in local changes
      if (path1 == null) return -1;
      if (path2 == null) return 1;
      return path1.compareToIgnoreCase(path2);
    }

    private static String getPath(final Object patch) {
      String path = null;
      if (patch instanceof ShelvedBinaryFile) {
        final ShelvedBinaryFile binaryFile = (ShelvedBinaryFile) patch;
        path = binaryFile.BEFORE_PATH;
        path = (path == null) ? binaryFile.AFTER_PATH : path;
      } else if (patch instanceof ShelvedChange) {
        final ShelvedChange shelvedChange = (ShelvedChange)patch;
        path = shelvedChange.getBeforePath().replace('/', File.separatorChar);
      }
      if (path == null) {
        return null;
      }
      final int pos = path.lastIndexOf(File.separatorChar);
      return (pos >= 0) ? path.substring(pos + 1) : path;
    }
  }

  private static class ShelfTreeCellRenderer extends ColoredTreeCellRenderer {
    private final IssueLinkRenderer myIssueLinkRenderer;
    private final Map<Couple<String>, String> myMoveRenameInfo;
    private static final Icon PatchIcon = StdFileTypes.PATCH.getIcon();
    private static final Icon DisabledPatchIcon = AllIcons.Nodes.DisabledPointcut;

    public ShelfTreeCellRenderer(Project project, final Map<Couple<String>, String> moveRenameInfo) {
      myMoveRenameInfo = moveRenameInfo;
      myIssueLinkRenderer = new IssueLinkRenderer(project, this);
    }

    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      Object nodeValue = node.getUserObject();
      if (nodeValue instanceof ShelvedChangeList) {
        ShelvedChangeList changeListData = (ShelvedChangeList) nodeValue;
        if (changeListData.isRecycled()) {
          myIssueLinkRenderer.appendTextWithLinks(changeListData.DESCRIPTION, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
          setIcon(DisabledPatchIcon);
        }
        else {
          myIssueLinkRenderer.appendTextWithLinks(changeListData.DESCRIPTION);
          setIcon(PatchIcon);
        }
        int count = node.getChildCount();
        String numFilesText = spaceAndThinSpace() + count + " " + StringUtil.pluralize("file", count) + ",";
        append(numFilesText, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        
        String date = DateFormatUtil.formatPrettyDateTime(changeListData.DATE);
        append(" " + date, SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else if (nodeValue instanceof ShelvedChange) {
        ShelvedChange change = (ShelvedChange) nodeValue;
        final String movedMessage = myMoveRenameInfo.get(Couple.of(change.getBeforePath(), change.getAfterPath()));
        renderFileName(change.getBeforePath(), change.getFileStatus(), movedMessage);
      }
      else if (nodeValue instanceof ShelvedBinaryFile) {
        ShelvedBinaryFile binaryFile = (ShelvedBinaryFile) nodeValue;
        String path = binaryFile.BEFORE_PATH;
        if (path == null) {
          path = binaryFile.AFTER_PATH;
        }
        final String movedMessage = myMoveRenameInfo.get(Couple.of(binaryFile.BEFORE_PATH, binaryFile.AFTER_PATH));
        renderFileName(path, binaryFile.getFileStatus(), movedMessage);
      }
    }

    private void renderFileName(String path, final FileStatus fileStatus, final String movedMessage) {
      path = path.replace('/', File.separatorChar);
      int pos = path.lastIndexOf(File.separatorChar);
      String fileName;
      String directory;
      if (pos >= 0) {
        directory = path.substring(0, pos).replace(File.separatorChar, File.separatorChar);
        fileName = path.substring(pos+1);
      }
      else {
        directory = "<project root>";
        fileName = path;
      }
      append(fileName, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fileStatus.getColor()));
      if (movedMessage != null) {
        append(movedMessage, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      append(spaceAndThinSpace() + directory, SimpleTextAttributes.GRAYED_ATTRIBUTES);
      setIcon(FileTypeManager.getInstance().getFileTypeByFileName(fileName).getIcon());
    }
  }

  private class MyChangeListDeleteProvider implements DeleteProvider {
    public void deleteElement(@NotNull DataContext dataContext) {
      //noinspection unchecked
      final List<ShelvedChangeList> shelvedChangeLists = getLists(dataContext);
      if (shelvedChangeLists.isEmpty()) return;
      String message = (shelvedChangeLists.size() == 1)
        ? VcsBundle.message("shelve.changes.delete.confirm", shelvedChangeLists.get(0).DESCRIPTION)
        : VcsBundle.message("shelve.changes.delete.multiple.confirm", shelvedChangeLists.size());
      int rc = Messages.showOkCancelDialog(myProject, message, VcsBundle.message("shelvedChanges.delete.title"), CommonBundle.message("button.delete"), CommonBundle.getCancelButtonText(), Messages.getWarningIcon());
      if (rc != Messages.OK) return;
      for(ShelvedChangeList changeList: shelvedChangeLists) {
        ShelveChangesManager.getInstance(myProject).deleteChangeList(changeList);
      }
    }

    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      //noinspection unchecked
      return ! getLists(dataContext).isEmpty();
    }

    private List<ShelvedChangeList> getLists(final DataContext dataContext) {
      final ShelvedChangeList[] shelved = SHELVED_CHANGELIST_KEY.getData(dataContext);
      final ShelvedChangeList[] recycled = SHELVED_RECYCLED_CHANGELIST_KEY.getData(dataContext);

      final List<ShelvedChangeList> shelvedChangeLists = (shelved == null && recycled == null) ?
                                                         Collections.<ShelvedChangeList>emptyList() : new ArrayList<ShelvedChangeList>();
      if (shelved != null) {
        ContainerUtil.addAll(shelvedChangeLists, shelved);
      }
      if (recycled != null) {
        ContainerUtil.addAll(shelvedChangeLists, recycled);
      }
      return shelvedChangeLists;
    }
  }

  private class MyChangesDeleteProvider implements DeleteProvider {
    public void deleteElement(@NotNull DataContext dataContext) {
      final Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project == null) return;
      final ShelvedChangeList[] shelved = SHELVED_CHANGELIST_KEY.getData(dataContext);
      if (shelved == null || (shelved.length != 1)) return;
      final List<ShelvedChange> changes = SHELVED_CHANGE_KEY.getData(dataContext);
      final List<ShelvedBinaryFile> binaryFiles = SHELVED_BINARY_FILE_KEY.getData(dataContext);

      final ShelvedChangeList list = shelved[0];

      final String message = VcsBundle.message("shelve.changes.delete.files.from.list", (changes == null ? 0 : changes.size()) +
                                                                                        (binaryFiles == null ? 0 : binaryFiles.size()));
      int rc = Messages.showOkCancelDialog(myProject, message, VcsBundle.message("shelve.changes.delete.files.from.list.title"), Messages.getWarningIcon());
      if (rc != Messages.OK) return;

      final ArrayList<ShelvedBinaryFile> oldBinaries = new ArrayList<ShelvedBinaryFile>(list.getBinaryFiles());
      final ArrayList<ShelvedChange> oldChanges = new ArrayList<ShelvedChange>(list.getChanges(project));

      oldBinaries.removeAll(binaryFiles);
      oldChanges.removeAll(changes);

      final CommitContext commitContext = new CommitContext();
      final List<FilePatch> patches = new ArrayList<FilePatch>();
      final List<VcsException> exceptions = new ArrayList<VcsException>();
      for (ShelvedChange change : oldChanges) {
        try {
          patches.add(change.loadFilePatch(myProject, commitContext));
        }
        catch (IOException e) {
          //noinspection ThrowableInstanceNeverThrown
          exceptions.add(new VcsException(e));
        }
        catch (PatchSyntaxException e) {
          //noinspection ThrowableInstanceNeverThrown
          exceptions.add(new VcsException(e));
        }
      }

      myShelveChangesManager.saveRemainingPatches(list, patches, oldBinaries, commitContext);

      if (! exceptions.isEmpty()) {
        String title = list.DESCRIPTION == null ? "" : list.DESCRIPTION;
        title = title.substring(0, Math.min(10, list.DESCRIPTION.length()));
        AbstractVcsHelper.getInstance(myProject).showErrors(exceptions, "Deleting files from '" + title + "'");
      }
    }

    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      final ShelvedChangeList[] shelved = SHELVED_CHANGELIST_KEY.getData(dataContext);
      if (shelved == null || (shelved.length != 1)) return false;
      final List<ShelvedChange> changes = SHELVED_CHANGE_KEY.getData(dataContext);
      if (changes != null && (! changes.isEmpty())) return true;
      final List<ShelvedBinaryFile> binaryFiles = SHELVED_BINARY_FILE_KEY.getData(dataContext);
      return (binaryFiles != null && (! binaryFiles.isEmpty()));
    }
  }

  private class ShelvedChangeDeleteProvider implements DeleteProvider {
    private final List<DeleteProvider> myProviders;

    private ShelvedChangeDeleteProvider() {
      myProviders = Arrays.asList(new MyChangesDeleteProvider(), new MyChangeListDeleteProvider());
    }

    @Nullable
    private DeleteProvider selectDelegate(final DataContext dataContext) {
      for (DeleteProvider provider : myProviders) {
        if (provider.canDeleteElement(dataContext)) {
          return provider;
        }
      }
      return null;
    }

    public void deleteElement(@NotNull DataContext dataContext) {
      final DeleteProvider delegate = selectDelegate(dataContext);
      if (delegate != null) {
        delegate.deleteElement(dataContext);
      }
    }

    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      return selectDelegate(dataContext) != null;
    }
  }
}
