// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.diff.DiffContentFactoryEx;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.impl.CacheDiffRequestProcessor;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.ide.dnd.*;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.DiffPreviewUpdateProcessor;
import com.intellij.openapi.vcs.changes.DnDActivateOnHoldTargetContent;
import com.intellij.openapi.vcs.changes.PreviewDiffSplitterComponent;
import com.intellij.openapi.vcs.changes.actions.ShowDiffPreviewAction;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vcs.changes.patch.tool.PatchDiffRequest;
import com.intellij.openapi.vcs.changes.ui.ChangeListDragBean;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.changes.ui.ShelvedChangeListDragBean;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.IconUtil.IconSizeWrapper;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.vcsUtil.VcsUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.icons.AllIcons.Vcs.Patch_applied;
import static com.intellij.openapi.actionSystem.Anchor.AFTER;
import static com.intellij.openapi.vcs.changes.shelf.DiffShelvedChangesActionProvider.createAppliedTextPatch;
import static com.intellij.util.FontUtil.spaceAndThinSpace;
import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.containers.ContainerUtil.*;

public class ShelvedChangesViewManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(ShelvedChangesViewManager.class);
  @NonNls static final String SHELF_CONTEXT_MENU = "Vcs.Shelf.ContextMenu";
  private static final String SHELVE_PREVIEW_SPLITTER_PROPORTION = "ShelvedChangesViewManager.DETAILS_SPLITTER_PROPORTION";

  private final ChangesViewContentManager myContentManager;
  private final ShelveChangesManager myShelveChangesManager;
  private final Project myProject;
  final ShelfTree myTree;
  private MyShelfContent myContent = null;
  final DeleteProvider myDeleteProvider = new MyShelveDeleteProvider();
  private final MergingUpdateQueue myUpdateQueue;
  private final VcsConfiguration myVcsConfiguration;

  public static final DataKey<List<ShelvedChangeList>> SHELVED_CHANGELIST_KEY =
    DataKey.create("ShelveChangesManager.ShelvedChangeListData");
  public static final DataKey<List<ShelvedChangeList>> SHELVED_RECYCLED_CHANGELIST_KEY =
    DataKey.create("ShelveChangesManager.ShelvedRecycledChangeListData");
  public static final DataKey<List<ShelvedChangeList>> SHELVED_DELETED_CHANGELIST_KEY =
    DataKey.create("ShelveChangesManager.ShelvedDeletedChangeListData");
  public static final DataKey<List<ShelvedChange>> SHELVED_CHANGE_KEY = DataKey.create("ShelveChangesManager.ShelvedChange");
  public static final DataKey<List<ShelvedBinaryFile>> SHELVED_BINARY_FILE_KEY = DataKey.create("ShelveChangesManager.ShelvedBinaryFile");
  private static final Object ROOT_NODE_VALUE = new Object();
  private DefaultMutableTreeNode myRoot;
  private final Map<Couple<String>, String> myMoveRenameInfo;
  private PreviewDiffSplitterComponent mySplitterComponent;

  public static ShelvedChangesViewManager getInstance(Project project) {
    return project.getComponent(ShelvedChangesViewManager.class);
  }

  public ShelvedChangesViewManager(Project project, ChangesViewContentManager contentManager, ShelveChangesManager shelveChangesManager,
                                   final MessageBus bus, StartupManager startupManager) {
    myProject = project;
    myContentManager = contentManager;
    myShelveChangesManager = shelveChangesManager;
    myUpdateQueue = new MergingUpdateQueue("Update Shelf Content", 200, true, null, myProject, null, true);
    myVcsConfiguration = VcsConfiguration.getInstance(myProject);
    bus.connect().subscribe(ShelveChangesManager.SHELF_TOPIC, new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        myUpdateQueue.queue(new MyContentUpdater());
      }
    });
    myMoveRenameInfo = new HashMap<>();

    myTree = new ShelfTree();
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setEditable(true);
    myTree.setDragEnabled(true);
    myTree.setCellRenderer(new ShelfTreeCellRenderer(project, myMoveRenameInfo));
    DefaultTreeCellEditor treeCellEditor = new DefaultTreeCellEditor(myTree, null) {
      @Override
      public boolean isCellEditable(EventObject event) {
        return !(event instanceof MouseEvent) && super.isCellEditable(event);
      }
    };
    myTree.setCellEditor(treeCellEditor);
    treeCellEditor.addCellEditorListener(new CellEditorListener() {
      @Override
      public void editingStopped(ChangeEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node instanceof ShelvedListNode && e.getSource() instanceof TreeCellEditor) {
          String editorValue = ((TreeCellEditor)e.getSource()).getCellEditorValue().toString();
          ShelvedChangeList shelvedChangeList = ((ShelvedListNode)node).getList();
          ShelveChangesManager.getInstance(project).renameChangeList(shelvedChangeList, editorValue);
          myTree.getModel().valueForPathChanged(TreeUtil.getPathFromRoot(node), shelvedChangeList);
        }
      }

      @Override
      public void editingCanceled(ChangeEvent e) {
      }
    });
    new TreeLinkMouseListener(new ShelfTreeCellRenderer(project, myMoveRenameInfo)).installOn(myTree);

    final AnAction showDiffAction = ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_DIFF_COMMON);
    showDiffAction.registerCustomShortcutSet(showDiffAction.getShortcutSet(), myTree);
    final EditSourceAction editSourceAction = new EditSourceAction();
    editSourceAction.registerCustomShortcutSet(editSourceAction.getShortcutSet(), myTree);

    PopupHandler.installPopupHandler(myTree, "ShelvedChangesPopupMenu", SHELF_CONTEXT_MENU);

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        DataContext dc = DataManager.getInstance().getDataContext(myTree);
        if (getShelveChanges(dc).isEmpty() && getBinaryShelveChanges(dc).isEmpty()) return false;
        DiffShelvedChangesActionProvider.showShelvedChangesDiff(dc);
        return true;
      }
    }.installOn(myTree);

    new TreeSpeedSearch(myTree, o -> {
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
    }, true);
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        mySplitterComponent.updatePreview(false);
      }
    });

    if (startupManager == null) {
      LOG.error("Couldn't start loading shelved changes");
      return;
    }
    startupManager.registerPostStartupActivity((DumbAwareRunnable)() -> myUpdateQueue.queue(new MyContentUpdater()));
  }

  @CalledInAwt
  private void updateChangesContent() {
    if (myShelveChangesManager.getAllLists().isEmpty()) {
      if (myContent != null) {
        myContentManager.removeContent(myContent);
        myContentManager.selectContent(ChangesViewContentManager.LOCAL_CHANGES);
        VcsNotifier.getInstance(myProject).hideAllNotificationsByType(ShelfNotification.class);
      }
      myContent = null;
    }
    else {
      if (myContent == null) {
        myTree.updateUI();
        JPanel rootPanel = createRootPanel();
        myContent = new MyShelfContent(rootPanel, VcsBundle.message("shelf.tab"), false);
        myContent.setCloseable(false);
        myContentManager.addContent(myContent);
        DnDSupport.createBuilder(myTree)
          .setImageProvider(this::createDraggedImage)
          .setBeanProvider(this::createDragStartBean)
          .setTargetChecker(myContent)
          .setDropHandler(myContent)
          .setDisposableParent(myContent)
          .install();
      }
      TreeState state = TreeState.createOn(myTree);
      myTree.setModel(buildChangesModel());
      state.applyTo(myTree);
    }
  }

  private ToolWindow getVcsToolWindow() {
    return ToolWindowManager.getInstance(myProject).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
  }

  @NotNull
  private JPanel createRootPanel() {
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree, SideBorder.LEFT);

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.addAll((ActionGroup)ActionManager.getInstance().getAction("ShelvedChangesToolbar"));
    actionGroup.add(new MyToggleDetailsAction(), new Constraints(AFTER, "ShelvedChanges.ShowHideDeleted"));

    MyShelvedPreviewProcessor changeProcessor = new MyShelvedPreviewProcessor(myProject);
    mySplitterComponent = new PreviewDiffSplitterComponent(pane, changeProcessor, SHELVE_PREVIEW_SPLITTER_PROPORTION,
                                                           myVcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("ShelvedChanges", actionGroup, false);

    JPanel rootPanel = new JPanel(new BorderLayout());
    rootPanel.add(toolbar.getComponent(), BorderLayout.WEST);
    rootPanel.add(mySplitterComponent, BorderLayout.CENTER);
    DataManager.registerDataProvider(rootPanel, myTree);

    return rootPanel;
  }

  private TreeModel buildChangesModel() {
    myRoot = new DefaultMutableTreeNode(ROOT_NODE_VALUE);   // not null for TreeState matching to work
    DefaultTreeModel model = new DefaultTreeModel(myRoot);
    final List<ShelvedChangeList> changeLists = new ArrayList<>(myShelveChangesManager.getShelvedChangeLists());
    if (myShelveChangesManager.isShowRecycled()) {
      changeLists.addAll(myShelveChangesManager.getRecycledShelvedChangeLists());
    }
    changeLists.sort(ChangelistComparator.getInstance());
    myMoveRenameInfo.clear();

    changeLists.forEach(changeList -> model.insertNodeInto(createShelvedListNode(changeList), myRoot, myRoot.getChildCount()));

    DefaultMutableTreeNode recentlyDeleted = new DefaultMutableTreeNode("Recently Deleted");
    List<ShelvedChangeList> deleted = new ArrayList<>(myShelveChangesManager.getDeletedLists());
    deleted.sort(ChangelistComparator.getInstance());
    deleted.forEach(d -> recentlyDeleted.add(createShelvedListNode(d)));
    model.insertNodeInto(recentlyDeleted, myRoot, myRoot.getChildCount());

    return model;
  }

  @NotNull
  private DefaultMutableTreeNode createShelvedListNode(@NotNull ShelvedChangeList changeList) {
    DefaultMutableTreeNode shelvedListNode = new ShelvedListNode(changeList);

    final List<Object> shelvedFilesNodes = new ArrayList<>();
    List<ShelvedChange> changes = changeList.getChanges(myProject);
    for (ShelvedChange change : changes) {
      putMovedMessage(change.getBeforePath(), change.getAfterPath());
      shelvedFilesNodes.add(change);
    }
    List<ShelvedBinaryFile> binaryFiles = changeList.getBinaryFiles();
    for (ShelvedBinaryFile file : binaryFiles) {
      putMovedMessage(file.BEFORE_PATH, file.AFTER_PATH);
      shelvedFilesNodes.add(file);
    }
    shelvedFilesNodes.sort(ShelvedFilePatchComparator.getInstance());

    shelvedFilesNodes.forEach(fNode -> shelvedListNode.add(new DefaultMutableTreeNode(fNode)));
    return shelvedListNode;
  }

  @CalledInAwt
  public void startEditing(@NotNull ShelvedChangeList shelvedChangeList) {
    runAfterUpdate(() -> {
      selectShelvedList(shelvedChangeList);
      myTree.startEditingAtPath(myTree.getLeadSelectionPath());
    });
  }

  static class ChangelistComparator implements Comparator<ShelvedChangeList> {
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

  public void activateView(@Nullable final ShelvedChangeList list) {
    runAfterUpdate(() -> {
      if (myContent == null) return;

      if (list != null) {
        selectShelvedList(list);
      }
      myContentManager.setSelectedContent(myContent);
      ToolWindow window = getVcsToolWindow();
      if (window != null && !window.isVisible()) {
        window.activate(null);
      }
    });
  }

  private void runAfterUpdate(@NotNull Runnable postUpdateRunnable) {
    GuiUtils.invokeLaterIfNeeded(() -> {
      myUpdateQueue.cancelAllUpdates();
      updateChangesContent();
      postUpdateRunnable.run();
    }, ModalityState.NON_MODAL);
  }

  @Override
  public void dispose() {
    myUpdateQueue.cancelAllUpdates();
  }

  public void selectShelvedList(@NotNull ShelvedChangeList list) {
    DefaultMutableTreeNode treeNode = TreeUtil.findNodeWithObject(myRoot, list);
    if (treeNode == null) {
      LOG.warn(String.format("Shelved changeList %s not found", list.DESCRIPTION));
      return;
    }
    TreeUtil.selectNode(myTree, treeNode);
  }

  private class ShelfTree extends Tree implements DataProvider {

    @Override
    public boolean isPathEditable(TreePath path) {
      return isEditable() && myTree.getSelectionCount() == 1 && path.getLastPathComponent() instanceof ShelvedListNode;
    }

    @Nullable
    @Override
    public Object getData(@NotNull @NonNls String dataId) {
      if (SHELVED_CHANGELIST_KEY.is(dataId)) {
        return newArrayList(getSelectedLists(l -> !l.isRecycled() && !l.isDeleted()));
      }
      else if (SHELVED_RECYCLED_CHANGELIST_KEY.is(dataId)) {
        return newArrayList(getSelectedLists(l -> l.isRecycled() && !l.isDeleted()));
      }
      else if (SHELVED_DELETED_CHANGELIST_KEY.is(dataId)) {
        return newArrayList(getSelectedLists(l -> l.isDeleted()));
      }
      else if (SHELVED_CHANGE_KEY.is(dataId)) {
        return TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class);
      }
      else if (SHELVED_BINARY_FILE_KEY.is(dataId)) {
        return TreeUtil.collectSelectedObjectsOfType(this, ShelvedBinaryFile.class);
      }
      else if (VcsDataKeys.HAVE_SELECTED_CHANGES.is(dataId)) {
        return getSelectionCount() > 0;
      }
      else if (VcsDataKeys.CHANGES.is(dataId)) {
        List<ShelvedChange> shelvedChanges = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class);
        final List<ShelvedBinaryFile> shelvedBinaryFiles = TreeUtil.collectSelectedObjectsOfType(this, ShelvedBinaryFile.class);
        if (!shelvedChanges.isEmpty() || !shelvedBinaryFiles.isEmpty()) {
          return ArrayUtil.toObjectArray(getChangesFromShelvedChanges(shelvedChanges, shelvedBinaryFiles), Change.class);
        }
        else {
          final List<ShelvedChangeList> changeLists = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChangeList.class);
          final List<Change> changes = new ArrayList<>();
          for (ShelvedChangeList list : changeLists) {
            changes.addAll(getChangesFromShelvedChanges(list.getChanges(myProject), list.getBinaryFiles()));
          }
          return ArrayUtil.toObjectArray(changes, Change.class);
        }
      }
      else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
        return myDeleteProvider;
      }
      else if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
        List<ShelvedChange> shelvedChanges = new ArrayList<>(TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class));
        final ArrayDeque<Navigatable> navigatables = new ArrayDeque<>();
        final List<ShelvedChangeList> changeLists = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChangeList.class);
        for (ShelvedChangeList changeList : changeLists) {
          shelvedChanges.addAll(changeList.getChanges(myProject));
        }
        for (final ShelvedChange shelvedChange : shelvedChanges) {
          if (shelvedChange.getBeforePath() != null && !FileStatus.ADDED.equals(shelvedChange.getFileStatus())) {
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
        return navigatables.toArray(new Navigatable[0]);
      }
      return null;
    }

    @NotNull
    private List<Change> getChangesFromShelvedChanges(@NotNull List<? extends ShelvedChange> shelvedChanges,
                                                      @NotNull List<? extends ShelvedBinaryFile> shelvedBinaryFiles) {
      final List<Change> changes = new ArrayList<>(shelvedChanges.size() + shelvedBinaryFiles.size());
      for (ShelvedChange shelvedChange : shelvedChanges) {
        changes.add(shelvedChange.getChange(myProject));
      }
      for (ShelvedBinaryFile binaryFile : shelvedBinaryFiles) {
        changes.add(binaryFile.createChange(myProject));
      }
      return changes;
    }

    @NotNull
    private Set<ShelvedChangeList> getSelectedLists(@NotNull Predicate<? super ShelvedChangeList> condition) {
      TreePath[] selectionPaths = getSelectionPaths();
      if (selectionPaths == null) return Collections.emptySet();
      return StreamEx.of(selectionPaths)
        .map(path -> TreeUtil.findObjectInPath(path, ShelvedChangeList.class))
        .filter(Objects::nonNull)
        .filter(condition)
        .collect(Collectors.toSet());
    }
  }

  @NotNull
  public static List<ShelvedChangeList> getShelvedLists(@NotNull final DataContext dataContext) {
    List<ShelvedChangeList> shelvedChangeLists = newArrayList();
    addAll(shelvedChangeLists, notNullize(SHELVED_CHANGELIST_KEY.getData(dataContext)));
    addAll(shelvedChangeLists, notNullize(SHELVED_RECYCLED_CHANGELIST_KEY.getData(dataContext)));
    addAll(shelvedChangeLists, notNullize(SHELVED_DELETED_CHANGELIST_KEY.getData(dataContext)));
    return shelvedChangeLists;
  }

  @NotNull
  public static List<ShelvedChange> getShelveChanges(@NotNull final DataContext dataContext) {
    return notNullize(dataContext.getData(SHELVED_CHANGE_KEY));
  }

  @NotNull
  public static List<ShelvedBinaryFile> getBinaryShelveChanges(@NotNull final DataContext dataContext) {
    return notNullize(dataContext.getData(SHELVED_BINARY_FILE_KEY));
  }

  private final static class ShelvedFilePatchComparator implements Comparator<Object> {
    private final static ShelvedFilePatchComparator ourInstance = new ShelvedFilePatchComparator();

    public static ShelvedFilePatchComparator getInstance() {
      return ourInstance;
    }

    @Override
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
    private static final Icon AppliedPatchIcon =
      new IconSizeWrapper(Patch_applied, Patch_applied.getIconWidth(), Patch_applied.getIconHeight()) {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
          GraphicsUtil.paintWithAlpha(g, 0.6f);
          super.paintIcon(c, g, x, y);
        }
      };
    private static final Icon DisabledToDeleteIcon = IconUtil.desaturate(AllIcons.Actions.GC);

    ShelfTreeCellRenderer(Project project, final Map<Couple<String>, String> moveRenameInfo) {
      myMoveRenameInfo = moveRenameInfo;
      myIssueLinkRenderer = new IssueLinkRenderer(project, this);
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      Object nodeValue = node.getUserObject();
      if (nodeValue instanceof ShelvedChangeList) {
        ShelvedChangeList changeListData = (ShelvedChangeList)nodeValue;
        if (changeListData.isRecycled() || changeListData.isDeleted()) {
          myIssueLinkRenderer.appendTextWithLinks(changeListData.DESCRIPTION, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
          setIcon(changeListData.isMarkedToDelete() || changeListData.isDeleted() ? DisabledToDeleteIcon : AppliedPatchIcon);
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
      else if (nodeValue != null) {
        myIssueLinkRenderer.appendTextWithLinks(nodeValue.toString());
      }
    }

    private void renderFileName(String path, final FileStatus fileStatus, final String movedMessage) {
      path = path.replace('/', File.separatorChar);
      int pos = path.lastIndexOf(File.separatorChar);
      String fileName;
      String directory;
      if (pos >= 0) {
        directory = path.substring(0, pos);
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

  private class MyShelveDeleteProvider implements DeleteProvider {

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      final Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project == null) return;

      List<ShelvedChangeList> shelvedListsToDelete = TreeUtil.collectSelectedObjectsOfType(myTree, ShelvedChangeList.class);

      List<ShelvedChange> changesToDelete = getChangesNotInLists(shelvedListsToDelete, getShelveChanges(dataContext));
      List<ShelvedBinaryFile> binariesToDelete = getBinariesNotInLists(shelvedListsToDelete, getBinaryShelveChanges(dataContext));

      int fileListSize = binariesToDelete.size() + changesToDelete.size();
      Map<ShelvedChangeList, Date> createdDeletedListsWithOriginalDates =
        myShelveChangesManager.deleteShelves(shelvedListsToDelete, getShelvedLists(dataContext), changesToDelete, binariesToDelete);
      if (!createdDeletedListsWithOriginalDates.isEmpty()) {
        showUndoDeleteNotification(shelvedListsToDelete, fileListSize, createdDeletedListsWithOriginalDates);
      }
    }

    private void showUndoDeleteNotification(@NotNull List<? extends ShelvedChangeList> shelvedListsToDelete,
                                            int fileListSize,
                                            @NotNull Map<ShelvedChangeList, Date> createdDeletedListsWithOriginalDate) {
      String message = constructDeleteSuccessfullyMessage(fileListSize, shelvedListsToDelete.size(), getFirstItem(shelvedListsToDelete));
      Notification shelfDeletionNotification = new ShelfDeleteNotification(message);
      shelfDeletionNotification.addAction(new UndoShelfDeletionAction(createdDeletedListsWithOriginalDate));
      shelfDeletionNotification.addAction(ActionManager.getInstance().getAction("ShelvedChanges.ShowRecentlyDeleted"));
      VcsNotifier.getInstance(myProject).showNotificationAndHideExisting(shelfDeletionNotification, ShelfDeleteNotification.class);
    }

    private class UndoShelfDeletionAction extends NotificationAction {
      @NotNull private final Map<ShelvedChangeList, Date> myListDateMap;

      private UndoShelfDeletionAction(@NotNull Map<ShelvedChangeList, Date> listDateMap) {
        super("Undo");
        myListDateMap = listDateMap;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        ShelveChangesManager manager = ShelveChangesManager.getInstance(myProject);
        List cantRestoreList = findAll(myListDateMap.keySet(), l -> !myShelveChangesManager.getDeletedLists().contains(l));
        myListDateMap.forEach((l, d) -> manager.restoreList(l, d));
        notification.expire();
        if (!cantRestoreList.isEmpty()) {
          VcsNotifier.getInstance(myProject).notifyMinorWarning("Undo Shelf Deletion", VcsBundle
            .message("shelve.changes.restore.error", cantRestoreList.size(), StringUtil.pluralize("changelist", cantRestoreList.size())));
        }
      }
    }

    private List<ShelvedBinaryFile> getBinariesNotInLists(@NotNull List<? extends ShelvedChangeList> listsToDelete,
                                                          @NotNull List<? extends ShelvedBinaryFile> binaryFiles) {
      List<ShelvedBinaryFile> result = new ArrayList<>(binaryFiles);
      for (ShelvedChangeList list : listsToDelete) {
        result.removeAll(list.getBinaryFiles());
      }
      return result;
    }

    @NotNull
    private List<ShelvedChange> getChangesNotInLists(@NotNull List<? extends ShelvedChangeList> listsToDelete,
                                                     @NotNull List<? extends ShelvedChange> shelvedChanges) {
      List<ShelvedChange> result = new ArrayList<>(shelvedChanges);
      for (ShelvedChangeList list : listsToDelete) {
        result.removeAll(list.getChanges(myProject));
      }
      return result;
    }

    @NotNull
    private String constructDeleteSuccessfullyMessage(int fileNum, int listNum, @Nullable ShelvedChangeList first) {
      StringBuilder stringBuilder = new StringBuilder();
      String delimiter = "";
      if (fileNum != 0) {
        stringBuilder.append(fileNum == 1 ? "one" : fileNum).append(StringUtil.pluralize(" file", fileNum));
        delimiter = " and ";
      }
      if (listNum != 0) {
        stringBuilder.append(delimiter);
        if (listNum == 1 && first != null) {
          stringBuilder.append("one shelved changelist [<b>").append(first.DESCRIPTION).append("</b>]");
        }
        else {
          stringBuilder.append(listNum).append(" shelved ").append(StringUtil.pluralize("changelist", listNum));
        }
      }
      stringBuilder.append(" deleted successfully");
      return StringUtil.capitalize(stringBuilder.toString());
    }

    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      return !getShelvedLists(dataContext).isEmpty();
    }
  }

  public class MyShelfContent extends DnDActivateOnHoldTargetContent {

    private MyShelfContent(JPanel panel, @NotNull String displayName, boolean isLockable) {
      super(myProject, panel, displayName, isLockable);
    }

    @Override
    public void drop(DnDEvent event) {
      super.drop(event);
      Object attachedObject = event.getAttachedObject();
      if (attachedObject instanceof ChangeListDragBean) {
        FileDocumentManager.getInstance().saveAllDocuments();
        List<Change> changes = Arrays.asList(((ChangeListDragBean)attachedObject).getChanges());
        myShelveChangesManager.shelveSilentlyUnderProgress(changes);
      }
    }

    @Override
    public boolean isDropPossible(@NotNull DnDEvent event) {
      Object attachedObject = event.getAttachedObject();
      return attachedObject instanceof ChangeListDragBean && ((ChangeListDragBean)attachedObject).getChanges().length > 0;
    }
  }

  @Nullable
  private DnDDragStartBean createDragStartBean(@NotNull DnDActionInfo info) {
    if (info.isMove()) {
      DataContext dc = DataManager.getInstance().getDataContext(myTree);
      return new DnDDragStartBean(new ShelvedChangeListDragBean(getShelveChanges(dc), getBinaryShelveChanges(dc), getShelvedLists(dc)));
    }
    return null;
  }

  @NotNull
  private DnDImage createDraggedImage(@NotNull DnDActionInfo info) {
    String imageText = "Unshelve changes";
    Image image = DnDAwareTree.getDragImage(myTree, imageText, null).getFirst();
    return new DnDImage(image, new Point(-image.getWidth(null), -image.getHeight(null)));
  }

  private class MyToggleDetailsAction extends ShowDiffPreviewAction {
    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      mySplitterComponent.setDetailsOn(state);
      myVcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN = state;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myVcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN;
    }
  }

  private class MyShelvedPreviewProcessor extends CacheDiffRequestProcessor<ShelvedWrapper> implements DiffPreviewUpdateProcessor {

    @NotNull private final DiffShelvedChangesActionProvider.PatchesPreloader myPreloader;
    @Nullable private ShelvedWrapper myCurrentShelvedElement;

    MyShelvedPreviewProcessor(@NotNull Project project) {
      super(project);
      myPreloader = new DiffShelvedChangesActionProvider.PatchesPreloader(project);
      Disposer.register(project, this);
    }

    @NotNull
    @Override
    protected String getRequestName(@NotNull ShelvedWrapper provider) {
      return provider.getRequestName();
    }

    @Override
    protected ShelvedWrapper getCurrentRequestProvider() {
      return myCurrentShelvedElement;
    }

    @CalledInAwt
    @Override
    public void clear() {
      myCurrentShelvedElement = null;
      updateRequest();
      dropCaches();
    }

    @Override
    @CalledInAwt
    public void refresh(boolean fromModelRefresh) {
      DataContext dc = DataManager.getInstance().getDataContext(myTree);
      List<ShelvedChange> selectedChanges = getShelveChanges(dc);
      List<ShelvedBinaryFile> selectedBinaryChanges = getBinaryShelveChanges(dc);

      if (selectedChanges.isEmpty() && selectedBinaryChanges.isEmpty()) {
        clear();
        return;
      }

      if (myCurrentShelvedElement != null) {
        if (keepBinarySelection(selectedBinaryChanges, myCurrentShelvedElement.getBinaryFile()) ||
            keepShelvedSelection(selectedChanges, myCurrentShelvedElement.getShelvedChange())) {
          dropCachesIfNeededAndUpdate(myCurrentShelvedElement);
          return;
        }
      }
      //getFirstSelected
      myCurrentShelvedElement = !selectedChanges.isEmpty()
                                ? new ShelvedWrapper(selectedChanges.get(0))
                                : new ShelvedWrapper(selectedBinaryChanges.get(0));
      dropCachesIfNeededAndUpdate(myCurrentShelvedElement);
    }

    private void dropCachesIfNeededAndUpdate(@NotNull ShelvedWrapper currentShelvedElement) {
      ShelvedChange shelvedChange = currentShelvedElement.getShelvedChange();
      boolean dropCaches = shelvedChange != null && myPreloader.isPatchFileChanged(shelvedChange.getPatchPath());
      if (dropCaches) {
        dropCaches();
      }
      updateRequest(dropCaches);
    }

    boolean keepShelvedSelection(@NotNull List<ShelvedChange> selectedChanges, @Nullable ShelvedChange currentShelvedChange) {
      return currentShelvedChange != null && selectedChanges.contains(currentShelvedChange);
    }

    boolean keepBinarySelection(@NotNull List<ShelvedBinaryFile> selectedBinaryChanges, @Nullable ShelvedBinaryFile currentBinary) {
      return currentBinary != null && selectedBinaryChanges.contains(currentBinary);
    }

    @NotNull
    @Override
    protected DiffRequest loadRequest(@NotNull ShelvedWrapper provider, @NotNull ProgressIndicator indicator)
      throws ProcessCanceledException, DiffRequestProducerException {
      try {
        ShelvedChange shelvedChange = provider.getShelvedChange();
        if (shelvedChange != null) {
          return new PatchDiffRequest(createAppliedTextPatch(myPreloader.getPatch(shelvedChange, null)));
        }

        DiffContentFactoryEx factory = DiffContentFactoryEx.getInstanceEx();
        ShelvedBinaryFile binaryFile = assertNotNull(provider.getBinaryFile());
        if (binaryFile.AFTER_PATH == null) {
          throw new DiffRequestProducerException("Content for '" + getRequestName(provider) + "' was removed");
        }
        byte[] binaryContent = binaryFile.createBinaryContentRevision(myProject).getBinaryContent();
        FileType fileType = VcsUtil.getFilePath(binaryFile.SHELVED_PATH).getFileType();
        return new SimpleDiffRequest(getRequestName(provider), factory.createEmpty(),
                                     factory.createBinary(myProject, binaryContent, fileType, getRequestName(provider)), null, null);
      }
      catch (VcsException | IOException e) {
        throw new DiffRequestProducerException("Can't show diff for '" + getRequestName(provider) + "'", e);
      }
    }
  }

  private static class ShelvedListNode extends DefaultMutableTreeNode {
    @NotNull private final ShelvedChangeList myList;

    ShelvedListNode(@NotNull ShelvedChangeList list) {
      super(list);
      myList = list;
    }

    @NotNull
    public ShelvedChangeList getList() {
      return myList;
    }
  }

  private class MyContentUpdater extends Update {
    MyContentUpdater() {
      super("ShelfContentUpdate");
    }

    @Override
    public void run() {
      updateChangesContent();
    }

    @Override
    public boolean canEat(Update update) {
      return true;
    }
  }
}
