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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.dnd.*;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SmartExpander;
import com.intellij.ui.TreeCopyProvider;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class ChangesListView extends Tree implements TypeSafeDataProvider, AdvancedDnDSource {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ui.ChangesListView");

  private ChangesListView.DropTarget myDropTarget;
  private DnDManager myDndManager;
  private ChangeListOwner myDragOwner;
  private final Project myProject;
  private TreeState myTreeState;
  private boolean myShowFlatten = false;
  private final CopyProvider myCopyProvider;

  @NonNls public static final String HELP_ID_KEY = "helpId";
  @NonNls public static final String ourHelpId = "ideaInterface.changes";
  @NonNls public static final DataKey<List<VirtualFile>> UNVERSIONED_FILES_DATA_KEY = DataKey.create("ChangeListView.UnversionedFiles");
  @NonNls public static final DataKey<List<FilePath>> MISSING_FILES_DATA_KEY = DataKey.create("ChangeListView.MissingFiles");
  @NonNls public static final DataKey<String> HELP_ID_DATA_KEY = DataKey.create(HELP_ID_KEY);

  private ActionGroup myMenuGroup;

  public ChangesListView(final Project project) {
    myProject = project;

    getModel().setRoot(ChangesBrowserNode.create(myProject, TreeModelBuilder.ROOT_NODE_VALUE));

    setShowsRootHandles(true);
    setRootVisible(false);

    new TreeSpeedSearch(this, new NodeToTextConvertor());
    SmartExpander.installOn(this);
    myCopyProvider = new TreeCopyProvider(this);
    new TreeLinkMouseListener(new ChangesBrowserNodeRenderer(myProject, false, false)).install(this);
  }

  public DefaultTreeModel getModel() {
    return (DefaultTreeModel)super.getModel();
  }

  public void installDndSupport(ChangeListOwner owner) {
    myDragOwner = owner;
    myDropTarget = new DropTarget();
    myDndManager = DnDManager.getInstance();

    myDndManager.registerSource(this);
    myDndManager.registerTarget(myDropTarget, this);
  }

  public void dispose() {
    if (myDropTarget != null) {
      myDndManager.unregisterSource(this);
      myDndManager.unregisterTarget(myDropTarget, this);

      myDropTarget = null;
      myDndManager = null;
      myDragOwner = null;
    }
  }

  private void storeState() {
    myTreeState = TreeState.createOn(this, (ChangesBrowserNode)getModel().getRoot());
  }

  private void restoreState() {
    myTreeState.applyTo(this, (ChangesBrowserNode)getModel().getRoot());
  }

  public boolean isShowFlatten() {
    return myShowFlatten;
  }

  public void setShowFlatten(final boolean showFlatten) {
    myShowFlatten = showFlatten;
  }

  public void updateModel(List<? extends ChangeList> changeLists, Trinity<List<VirtualFile>, Integer, Integer> unversionedFiles, final List<LocallyDeletedChange> locallyDeletedFiles,
                          List<VirtualFile> modifiedWithoutEditing,
                          MultiMap<String, VirtualFile> switchedFiles,
                          @Nullable Map<VirtualFile, String> switchedRoots,
                          @Nullable List<VirtualFile> ignoredFiles,
                          final List<VirtualFile> lockedFolders,
                          @Nullable final Map<VirtualFile, LogicalLock> logicallyLockedFiles) {
    storeState();

    TreeModelBuilder builder = new TreeModelBuilder(myProject, isShowFlatten());
    final DefaultTreeModel model = builder.buildModel(changeLists, unversionedFiles, locallyDeletedFiles, modifiedWithoutEditing, 
                                                      switchedFiles, switchedRoots, ignoredFiles, lockedFolders, logicallyLockedFiles);
    setModel(model);
    setCellRenderer(new ChangesBrowserNodeRenderer(myProject, isShowFlatten(), true));

    expandPath(new TreePath(((ChangesBrowserNode)model.getRoot()).getPath()));

    restoreState();
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key == VcsDataKeys.CHANGES) {
      sink.put(VcsDataKeys.CHANGES, getSelectedChanges());
    }
    else if (key == VcsDataKeys.CHANGE_LEAD_SELECTION) {
      sink.put(VcsDataKeys.CHANGE_LEAD_SELECTION, getLeadSelection());
    }
    else if (key == VcsDataKeys.CHANGE_LISTS) {
      sink.put(VcsDataKeys.CHANGE_LISTS, getSelectedChangeLists());
    }
    else if (key == PlatformDataKeys.VIRTUAL_FILE_ARRAY) {
      sink.put(PlatformDataKeys.VIRTUAL_FILE_ARRAY, getSelectedFiles());
    }
    else if (key == PlatformDataKeys.NAVIGATABLE) {
      final VirtualFile[] files = getSelectedFiles();
      if (files.length == 1 && !files [0].isDirectory()) {
        sink.put(PlatformDataKeys.NAVIGATABLE, new OpenFileDescriptor(myProject, files[0], 0));
      }
    }
    else if (key == PlatformDataKeys.NAVIGATABLE_ARRAY) {
      sink.put(PlatformDataKeys.NAVIGATABLE_ARRAY, ChangesUtil.getNavigatableArray(myProject, getSelectedFiles()));
    }
    else if (key == PlatformDataKeys.DELETE_ELEMENT_PROVIDER) {
      final TreePath[] paths = getSelectionPaths();
      if (paths != null) {
        for(TreePath path: paths) {
          ChangesBrowserNode node = (ChangesBrowserNode) path.getLastPathComponent();
          if (!(node.getUserObject() instanceof ChangeList)) {
            sink.put(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, new VirtualFileDeleteProvider());
            break;
          }
        }
      }
    }
    else if (key == PlatformDataKeys.COPY_PROVIDER) {
      sink.put(PlatformDataKeys.COPY_PROVIDER, myCopyProvider);
    }
    else if (key == UNVERSIONED_FILES_DATA_KEY) {
      sink.put(UNVERSIONED_FILES_DATA_KEY, getSelectedUnversionedFiles());
    }
    else if (key == VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY) {
      sink.put(VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY, getSelectedModifiedWithoutEditing());
    }
    else if (key == MISSING_FILES_DATA_KEY) {
      sink.put(MISSING_FILES_DATA_KEY, getSelectedMissingFiles());
    }
    else if (key == HELP_ID_DATA_KEY) {
      sink.put(HELP_ID_DATA_KEY, ourHelpId);
    }
    else if (key == VcsDataKeys.CHANGES_IN_LIST_KEY) {
      final TreePath selectionPath = getSelectionPath();
      if (selectionPath != null && selectionPath.getPathCount() > 1) {
        ChangesBrowserNode firstNode = (ChangesBrowserNode)selectionPath.getPathComponent(1);
        if (firstNode instanceof ChangesBrowserChangeListNode) {
          final List<Change> list = firstNode.getAllChangesUnder();
          sink.put(VcsDataKeys.CHANGES_IN_LIST_KEY, list);
        }
      }
    }
  }

  private List<VirtualFile> getSelectedUnversionedFiles() {
    return getSelectedVirtualFiles(ChangesBrowserNode.UNVERSIONED_FILES_TAG);
  }

  private List<VirtualFile> getSelectedModifiedWithoutEditing() {
    return getSelectedVirtualFiles(ChangesBrowserNode.MODIFIED_WITHOUT_EDITING_TAG);
  }

  private List<VirtualFile> getSelectedIgnoredFiles() {
    return getSelectedVirtualFiles(ChangesBrowserNode.IGNORED_FILES_TAG);
  }

  private List<VirtualFile> getSelectedVirtualFiles(final Object tag) {
    Set<VirtualFile> files = new HashSet<VirtualFile>();
    final TreePath[] paths = getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        if (path.getPathCount() > 1) {
          ChangesBrowserNode firstNode = (ChangesBrowserNode)path.getPathComponent(1);
          if (tag == null || firstNode.getUserObject() == tag) {
            ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
            files.addAll(node.getAllFilesUnder());
          }
        }
      }
    }
    return new ArrayList<VirtualFile>(files);
  }

  private List<FilePath> getSelectedMissingFiles() {
    List<FilePath> files = new ArrayList<FilePath>();
    final TreePath[] paths = getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
        files.addAll(node.getAllFilePathsUnder());
      }
    }
    return files;
  }

  protected VirtualFile[] getSelectedFiles() {
    final Change[] changes = getSelectedChanges();
    Collection<VirtualFile> files = new HashSet<VirtualFile>();
    for (Change change : changes) {
      final ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        final VirtualFile file = afterRevision.getFile().getVirtualFile();
        if (file != null && file.isValid()) {
          files.add(file);
        }
      }
    }

    files.addAll(getSelectedVirtualFiles(null));

    return VfsUtil.toVirtualFileArray(files);
  }

  private Change[] getLeadSelection() {
    final Set<Change> changes = new LinkedHashSet<Change>();

    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return new Change[0];

    for (TreePath path : paths) {
      ChangesBrowserNode node = (ChangesBrowserNode) path.getLastPathComponent();
      if (node instanceof ChangesBrowserChangeNode) {
        changes.add(((ChangesBrowserChangeNode) node).getUserObject());
      }
    }

    return changes.toArray(new Change[changes.size()]);
  }

  @NotNull
  private Change[] getSelectedChanges() {
    Set<Change> changes = new LinkedHashSet<Change>();

    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return new Change[0];

    for (TreePath path : paths) {
      ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
      changes.addAll(node.getAllChangesUnder());
    }

    if (changes.size() == 0) {
      final List<VirtualFile> selectedModifiedWithoutEditing = getSelectedModifiedWithoutEditing();
      if (selectedModifiedWithoutEditing != null && selectedModifiedWithoutEditing.size() > 0) {
        for(VirtualFile file: selectedModifiedWithoutEditing) {
          AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
          final DiffProvider diffProvider = vcs == null ? null : vcs.getDiffProvider();
          if (diffProvider != null) {
            ContentRevision beforeRevision = new VcsCurrentRevisionProxy(diffProvider, file);
            ContentRevision afterRevision = new CurrentContentRevision(new FilePathImpl(file));
            changes.add(new Change(beforeRevision, afterRevision, FileStatus.HIJACKED));
          }
        }
      }
    }

    return changes.toArray(new Change[changes.size()]);
  }

  @NotNull
  private ChangeList[] getSelectedChangeLists() {
    Set<ChangeList> lists = new HashSet<ChangeList>();

    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return new ChangeList[0];

    for (TreePath path : paths) {
      ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
      final Object userObject = node.getUserObject();
      if (userObject instanceof ChangeList) {
        lists.add((ChangeList)userObject);
      }
    }

    return lists.toArray(new ChangeList[lists.size()]);
  }

  public void setMenuActions(final ActionGroup menuGroup) {
    myMenuGroup = menuGroup;
    updateMenu();
    editSourceRegistration();
  }

  protected void editSourceRegistration() {
    EditSourceOnDoubleClickHandler.install(this);
    EditSourceOnEnterKeyHandler.install(this);
  }

  private void updateMenu() {
    PopupHandler.installPopupHandler(this, myMenuGroup, ActionPlaces.CHANGES_VIEW_POPUP, ActionManager.getInstance());
  }

  public void updateUI() {
    super.updateUI();
    if (myMenuGroup != null) {
      updateMenu();
    }
  }

  @SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
  private static class DragImageFactory {
    private static void drawSelection(JTable table, int column, Graphics g, final int width) {
      int y = 0;
      final int[] rows = table.getSelectedRows();
      final int height = table.getRowHeight();
      for (int row : rows) {
        final TableCellRenderer renderer = table.getCellRenderer(row, column);
        final Component component = renderer.getTableCellRendererComponent(table, table.getValueAt(row, column), false, false, row, column);
        g.translate(0, y);
        component.setBounds(0, 0, width, height);
        boolean wasOpaque = false;
        if (component instanceof JComponent) {
          final JComponent j = (JComponent)component;
          if (j.isOpaque()) wasOpaque = true;
          j.setOpaque(false);
        }
        component.paint(g);
        if (wasOpaque) {
          ((JComponent)component).setOpaque(true);
        }
        y += height;
        g.translate(0, -y);
      }
    }

    private static void drawSelection(JTree tree, Graphics g, final int width) {
      int y = 0;
      final int[] rows = tree.getSelectionRows();
      final int height = tree.getRowHeight();
      for (int row : rows) {
        final TreeCellRenderer renderer = tree.getCellRenderer();
        final Object value = tree.getPathForRow(row).getLastPathComponent();
        if (value == null) continue;
        final Component component = renderer.getTreeCellRendererComponent(tree, value, false, false, false, row, false);
        if (component.getFont() == null) {
          component.setFont(tree.getFont());
        }
        g.translate(0, y);
        component.setBounds(0, 0, width, height);
        boolean wasOpaque = false;
        if (component instanceof JComponent) {
          final JComponent j = (JComponent)component;
          if (j.isOpaque()) wasOpaque = true;
          j.setOpaque(false);
        }
        component.paint(g);
        if (wasOpaque) {
          ((JComponent)component).setOpaque(true);
        }
        y += height;
        g.translate(0, -y);
      }
    }

    public static Image createImage(final JTable table, int column) {
      final int height = Math.max(20, Math.min(100, table.getSelectedRowCount() * table.getRowHeight()));
      final int width = table.getColumnModel().getColumn(column).getWidth();

      final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = (Graphics2D)image.getGraphics();

      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));

      drawSelection(table, column, g2, width);
      return image;
    }

    public static Image createImage(final JTree tree) {
      final TreeSelectionModel model = tree.getSelectionModel();
      final TreePath[] paths = model.getSelectionPaths();

      int count = 0;
      final List<ChangesBrowserNode> nodes = new ArrayList<ChangesBrowserNode>();
      for (final TreePath path : paths) {
        final ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
        if (!node.isLeaf()) {
          nodes.add(node);
          count += node.getCount();
        }
      }

      for (TreePath path : paths) {
        final ChangesBrowserNode element = (ChangesBrowserNode)path.getLastPathComponent();
        boolean child = false;
        for (final ChangesBrowserNode node : nodes) {
          if (node.isNodeChild(element)) {
            child = true;
            break;
          }
        }

        if (!child) {
          if (element.isLeaf()) count++;
        } else if (!element.isLeaf()) {
          count -= element.getCount();
        }
      }

      final JLabel label = new JLabel(VcsBundle.message("changes.view.dnd.label", count));
      label.setOpaque(true);
      label.setForeground(tree.getForeground());
      label.setBackground(tree.getBackground());
      label.setFont(tree.getFont());
      label.setSize(label.getPreferredSize());
      final BufferedImage image = new BufferedImage(label.getWidth(), label.getHeight(), BufferedImage.TYPE_INT_ARGB);

      Graphics2D g2 = (Graphics2D)image.getGraphics();
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
      label.paint(g2);
      g2.dispose();

      return image;
    }


  }

  public class DropTarget implements DnDTarget {
    public boolean update(DnDEvent aEvent) {
      aEvent.hideHighlighter();
      aEvent.setDropPossible(false, "");

      Object attached = aEvent.getAttachedObject();
      if (!(attached instanceof ChangeListDragBean)) return false;

      final ChangeListDragBean dragBean = (ChangeListDragBean)attached;
      if (dragBean.getSourceComponent() != ChangesListView.this) return false;
      dragBean.setTargetNode(null);

      RelativePoint dropPoint = aEvent.getRelativePoint();
      Point onTree = dropPoint.getPoint(ChangesListView.this);
      final TreePath dropPath = getPathForLocation(onTree.x, onTree.y);

      if (dropPath == null) return false;

      ChangesBrowserNode dropNode = (ChangesBrowserNode)dropPath.getLastPathComponent();
      while(!((ChangesBrowserNode) dropNode.getParent()).isRoot()) {
        dropNode = (ChangesBrowserNode)dropNode.getParent();
      }

      if (!dropNode.canAcceptDrop(dragBean)) {
        return false;
      }

      final Rectangle tableCellRect = getPathBounds(new TreePath(dropNode.getPath()));
      if (fitsInBounds(tableCellRect)) {
        aEvent.setHighlighting(new RelativeRectangle(ChangesListView.this, tableCellRect), DnDEvent.DropTargetHighlightingType.RECTANGLE);
      }

      aEvent.setDropPossible(true, null);
      dragBean.setTargetNode(dropNode);

      return false;
    }

    public void drop(DnDEvent aEvent) {
      Object attached = aEvent.getAttachedObject();
      if (!(attached instanceof ChangeListDragBean)) return;

      final ChangeListDragBean dragBean = (ChangeListDragBean)attached;
      final ChangesBrowserNode changesBrowserNode = dragBean.getTargetNode();
      if (changesBrowserNode != null) {
        changesBrowserNode.acceptDrop(myDragOwner, dragBean);
      }
    }

    public void cleanUpOnLeave() {
    }

    public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
    }
  }

  private boolean fitsInBounds(final Rectangle rect) {
    final Container container = getParent();
    if (container instanceof JViewport) {
      final Container scrollPane = container.getParent();
      if (scrollPane instanceof JBScrollPane) {
        final Rectangle rectangle = SwingUtilities.convertRectangle(this, rect, scrollPane.getParent());
        return scrollPane.getBounds().contains(rectangle);
      }
    }
    return true;
  }

  private static class NodeToTextConvertor implements Convertor<TreePath, String> {
    public String convert(final TreePath path) {
      ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
      return node.getTextPresentation();
    }
  }

  public boolean canStartDragging(DnDAction action, Point dragOrigin) {
    return action == DnDAction.MOVE && 
           (getSelectedChanges().length > 0 || getSelectedUnversionedFiles().size() > 0 || getSelectedIgnoredFiles().size() > 0);
  }

  public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
    return new DnDDragStartBean(new ChangeListDragBean(this, getSelectedChanges(), getSelectedUnversionedFiles(),
                                                       getSelectedIgnoredFiles()));
  }

  @Nullable
  public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
    final Image image = DragImageFactory.createImage(this);
    return new Pair<Image, Point>(image, new Point(-image.getWidth(null), -image.getHeight(null)));
  }

  public void dragDropEnd() {
  }

  public void dropActionChanged(final int gestureModifiers) {
  }

  @NotNull
  public JComponent getComponent() {
    return this;
  }

  public void processMouseEvent(final MouseEvent e) {
    if (MouseEvent.MOUSE_RELEASED == e.getID() && !isSelectionEmpty() && !e.isShiftDown() && !e.isControlDown()  &&
        !e.isMetaDown() && !e.isPopupTrigger()) {
      if (isOverSelection(e.getPoint())) {
        clearSelection();
        final TreePath path = getPathForLocation(e.getPoint().x, e.getPoint().y);
        if (path != null) {
          setSelectionPath(path);
          e.consume();
        }
      }
    }


    super.processMouseEvent(e);
  }

  public boolean isOverSelection(final Point point) {
    return TreeUtil.isOverSelection(this, point);
  }

  public void dropSelectionButUnderPoint(final Point point) {
    TreeUtil.dropSelectionButUnderPoint(this, point);
  }
}
