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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.Icons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public abstract class ChangesTreeList<T> extends JPanel {
  private final Tree myTree;
  private final JBList myList;
  private final JScrollPane myTreeScrollPane;
  private final JScrollPane myListScrollPane;
  protected final Project myProject;
  private final boolean myShowCheckboxes;
  private final boolean myHighlightProblems;
  private boolean myShowFlatten;

  private final Collection<T> myIncludedChanges;
  private Runnable myDoubleClickHandler = EmptyRunnable.getInstance();

  @NonNls private static final String TREE_CARD = "Tree";
  @NonNls private static final String LIST_CARD = "List";
  @NonNls private static final String ROOT = "root";
  private final CardLayout myCards;

  @NonNls private final static String FLATTEN_OPTION_KEY = "ChangesBrowser.SHOW_FLATTEN";

  private final Runnable myInclusionListener;
  @Nullable private ChangeNodeDecorator myChangeDecorator;

  public ChangesTreeList(final Project project, Collection<T> initiallyIncluded, final boolean showCheckboxes,
                         final boolean highlightProblems, @Nullable final Runnable inclusionListener, @Nullable final ChangeNodeDecorator decorator) {
    myProject = project;
    myShowCheckboxes = showCheckboxes;
    myHighlightProblems = highlightProblems;
    myInclusionListener = inclusionListener;
    myChangeDecorator = decorator;
    myIncludedChanges = new HashSet<T>(initiallyIncluded);

    myCards = new CardLayout();

    setLayout(myCards);

    final int checkboxWidth = new JCheckBox().getPreferredSize().width;
    myTree = new Tree(ChangesBrowserNode.create(myProject, ROOT)) {
      public Dimension getPreferredScrollableViewportSize() {
        Dimension size = super.getPreferredScrollableViewportSize();
        size = new Dimension(size.width + 10, size.height);
        return size;
      }

      protected void processMouseEvent(MouseEvent e) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
          int row = myTree.getRowForLocation(e.getX(), e.getY());
          if (row >= 0) {
            final Rectangle baseRect = myTree.getRowBounds(row);
            baseRect.setSize(checkboxWidth, baseRect.height);
            if (baseRect.contains(e.getPoint())) {
              myTree.setSelectionRow(row);
              toggleSelection();
            }
          }
        }
        super.processMouseEvent(e);
      }

      public int getToggleClickCount() {
        return -1;
      }
    };

    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);

    myTree.setCellRenderer(new MyTreeCellRenderer());
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(TreePath o) {
        ChangesBrowserNode node = (ChangesBrowserNode) o.getLastPathComponent();
        return node.getTextPresentation();
      }
    });

    myList = new JBList(new DefaultListModel());
    myList.setVisibleRowCount(10);

    add(myListScrollPane = new JScrollPane(myList), LIST_CARD);
    add(myTreeScrollPane = new JScrollPane(myTree), TREE_CARD);

    new ListSpeedSearch(myList) {
      protected String getElementText(Object element) {
        if (element instanceof Change) {
          return ChangesUtil.getFilePath((Change)element).getName();
        }
        return super.getElementText(element);
      }
    };

    myList.setCellRenderer(new MyListCellRenderer());

    new MyToggleSelectionAction().registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), this);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        includeSelection();
      }

    }, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        excludeSelection();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        final int idx = myList.locationToIndex(e.getPoint());
        if (idx >= 0) {
          final Rectangle baseRect = myList.getCellBounds(idx, idx);
          baseRect.setSize(checkboxWidth, baseRect.height);
          if (baseRect.contains(e.getPoint())) {
            toggleSelection();
            e.consume();
          }
          else if (e.getClickCount() == 2) {
            myDoubleClickHandler.run();
            e.consume();
          }
        }
      }
    });

    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        final int row = myTree.getRowForLocation(e.getPoint().x, e.getPoint().y);
        if (row >= 0) {
          final Rectangle baseRect = myTree.getRowBounds(row);
          baseRect.setSize(checkboxWidth, baseRect.height);
          if (!baseRect.contains(e.getPoint()) && e.getClickCount() == 2) {
            myDoubleClickHandler.run();
            e.consume();
          }
        }
      }
    });

    setShowFlatten(PropertiesComponent.getInstance(myProject).isTrueValue(FLATTEN_OPTION_KEY));

    String emptyText = StringUtil.capitalize(DiffBundle.message("diff.count.differences.status.text", 0));
    myTree.setEmptyText(emptyText);
    myList.setEmptyText(emptyText);
  }

  public void setChangeDecorator(@Nullable ChangeNodeDecorator changeDecorator) {
    myChangeDecorator = changeDecorator;
  }

  public void setDoubleClickHandler(final Runnable doubleClickHandler) {
    myDoubleClickHandler = doubleClickHandler;
  }

  public void installPopupHandler(ActionGroup group) {
    PopupHandler.installUnknownPopupHandler(myList, group, ActionManager.getInstance());
    PopupHandler.installUnknownPopupHandler(myTree, group, ActionManager.getInstance());
  }

  public Dimension getPreferredSize() {
    return new Dimension(400, 400);
  }

  public boolean isShowFlatten() {
    return myShowFlatten;
  }

  public void setScrollPaneBorder(Border border) {
    myListScrollPane.setBorder(border);
    myTreeScrollPane.setBorder(border);
  }

  public void setShowFlatten(final boolean showFlatten) {
    final List<T> wasSelected = getSelectedChanges();
    myShowFlatten = showFlatten;
    myCards.show(this, myShowFlatten ? LIST_CARD : TREE_CARD);
    select(wasSelected);
    if (myList.hasFocus() || myTree.hasFocus()) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          requestFocus();
        }
      });
    }
  }


  public void requestFocus() {
    if (myShowFlatten) {
      myList.requestFocus();
    }
    else {
      myTree.requestFocus();
    }
  }

  public void setChangesToDisplay(final List<T> changes) {
    final DefaultListModel listModel = (DefaultListModel)myList.getModel();
    final List<T> sortedChanges = new ArrayList<T>(changes);
    Collections.sort(sortedChanges, new Comparator<T>() {
      public int compare(final T o1, final T o2) {
        return TreeModelBuilder.getPathForObject(o1).getName().compareToIgnoreCase(TreeModelBuilder.getPathForObject(o2).getName());
      }
    });

    listModel.removeAllElements();
    for (T change : sortedChanges) {
      listModel.addElement(change);
    }

    final DefaultTreeModel model = buildTreeModel(changes, myChangeDecorator);
    myTree.setModel(model);

    final Runnable runnable = new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        TreeUtil.expandAll(myTree);

        int listSelection = 0;
        int scrollRow = 0;
        if (myIncludedChanges.size() > 0) {
          int count = 0;
          for (T change : changes) {
            if (myIncludedChanges.contains(change)) {
              listSelection = count;
              break;
            }
            count++;
          }

          ChangesBrowserNode root = (ChangesBrowserNode)model.getRoot();
          Enumeration enumeration = root.depthFirstEnumeration();

          while (enumeration.hasMoreElements()) {
            ChangesBrowserNode node = (ChangesBrowserNode)enumeration.nextElement();
            final CheckboxTree.NodeState state = getNodeStatus(node);
            if (node != root && state == CheckboxTree.NodeState.CLEAR) {
              myTree.collapsePath(new TreePath(node.getPath()));
            }
          }

          enumeration = root.depthFirstEnumeration();
          while (enumeration.hasMoreElements()) {
            ChangesBrowserNode node = (ChangesBrowserNode)enumeration.nextElement();
            final CheckboxTree.NodeState state = getNodeStatus(node);
            if (state == CheckboxTree.NodeState.FULL && node.isLeaf()) {
              scrollRow = myTree.getRowForPath(new TreePath(node.getPath()));
              break;
            }
          }

        }
        if (changes.size() > 0) {
          myList.setSelectedIndex(listSelection);
          myList.ensureIndexIsVisible(listSelection);

          myTree.setSelectionRow(scrollRow);
          TreeUtil.showRowCentered(myTree, scrollRow, false);
        }
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run();
    } else {
      SwingUtilities.invokeLater(runnable);
    }
  }

  protected abstract DefaultTreeModel buildTreeModel(final List<T> changes, final ChangeNodeDecorator changeNodeDecorator);

  @SuppressWarnings({"SuspiciousMethodCalls"})
  private void toggleSelection() {
    boolean hasExcluded = false;
    for (T value : getSelectedChanges()) {
      if (!myIncludedChanges.contains(value)) {
        hasExcluded = true;
      }
    }

    if (hasExcluded) {
      includeSelection();
    }
    else {
      excludeSelection();
    }

    repaint();
  }

  private void includeSelection() {
    for (T change : getSelectedChanges()) {
      myIncludedChanges.add(change);
    }
    notifyInclusionListener();
    repaint();
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  private void excludeSelection() {
    for (T change : getSelectedChanges()) {
      myIncludedChanges.remove(change);
    }
    notifyInclusionListener();
    repaint();
  }

  public List<T> getChanges() {
    if (myShowFlatten) {
      ListModel m = myList.getModel();
      int size = m.getSize();
      List result = new ArrayList(size);
      for (int i = 0; i < size; i++) {
        result.add(m.getElementAt(i));
      }
      return result;
    }
    else {
      final LinkedHashSet result = new LinkedHashSet();
      TreeUtil.traverseDepth((ChangesBrowserNode)myTree.getModel().getRoot(), new TreeUtil.Traverse() {
        public boolean accept(Object node) {
          ChangesBrowserNode changeNode = (ChangesBrowserNode)node;
          if (changeNode.isLeaf()) result.addAll(changeNode.getAllChangesUnder());
          return true;
        }
      });
      return new ArrayList<T>(result);
    }
  }

  public int getSelectionCount() {
    if (myShowFlatten) {
      return myList.getSelectedIndices().length;
    } else {
      return myTree.getSelectionCount();
    }
  }

  @NotNull
  public List<T> getSelectedChanges() {
    if (myShowFlatten) {
      final Object[] o = myList.getSelectedValues();
      final List<T> changes = new ArrayList<T>();
      for (Object anO : o) {
        changes.add((T)anO);
      }

      return changes;
    }
    else {
      List<T> changes = new ArrayList<T>();
      final TreePath[] paths = myTree.getSelectionPaths();
      if (paths != null) {
        for (TreePath path : paths) {
          ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
          changes.addAll(getSelectedObjects(node));
        }
      }

      return changes;
    }
  }

  protected abstract List<T> getSelectedObjects(final ChangesBrowserNode<T> node);

  @Nullable
  protected abstract T getLeadSelectedObject(final ChangesBrowserNode node);

  @Nullable
  public T getHighestLeadSelection() {
    if (myShowFlatten) {
      final int index = myList.getLeadSelectionIndex();
      ListModel listModel = myList.getModel();
      if (index < 0 || index >= listModel.getSize()) return null;
      //noinspection unchecked
      return (T)listModel.getElementAt(index);
    }
    else {
      final TreePath path = myTree.getSelectionPath();
      if (path == null) return null;
      return getLeadSelectedObject((ChangesBrowserNode<T>)path.getLastPathComponent());
    }
  }

  @Nullable
  public T getLeadSelection() {
    if (myShowFlatten) {
      final int index = myList.getLeadSelectionIndex();
      ListModel listModel = myList.getModel();
      if (index < 0 || index >= listModel.getSize()) return null;
      //noinspection unchecked
      return (T)listModel.getElementAt(index);
    }
    else {
      final TreePath path = myTree.getSelectionPath();
      if (path == null) return null;
      final List<T> changes = getSelectedObjects(((ChangesBrowserNode<T>)path.getLastPathComponent()));
      return changes.size() > 0 ? changes.get(0) : null;
    }
  }

  private void notifyInclusionListener() {
    if (myInclusionListener != null) {
      myInclusionListener.run();
    }
  }

  // no listener supposed to be called
  public void setIncludedChanges(final Collection<T> changes) {
    myIncludedChanges.clear();
    myIncludedChanges.addAll(changes);
    myTree.repaint();
    myList.repaint();
  }

  public void includeChange(final T change) {
    myIncludedChanges.add(change);
    notifyInclusionListener();
    myTree.repaint();
    myList.repaint();
  }

  public void includeChanges(final Collection<T> changes) {
    myIncludedChanges.addAll(changes);
    notifyInclusionListener();
    myTree.repaint();
    myList.repaint();
  }

  public void excludeChange(final T change) {
    myIncludedChanges.remove(change);
    notifyInclusionListener();
    myTree.repaint();
    myList.repaint();
  }

  public void excludeChanges(final Collection<T> changes) {
    myIncludedChanges.removeAll(changes);
    notifyInclusionListener();
    myTree.repaint();
    myList.repaint();
  }

  public boolean isIncluded(final T change) {
    return myIncludedChanges.contains(change);
  }

  public Collection<T> getIncludedChanges() {
    return myIncludedChanges;
  }

  public void expandAll() {
    TreeUtil.expandAll(myTree);
  }

  public AnAction[] getTreeActions() {
    final ToggleShowDirectoriesAction directoriesAction = new ToggleShowDirectoriesAction();
    final ExpandAllAction expandAllAction = new ExpandAllAction(myTree) {
      public void update(AnActionEvent e) {
        e.getPresentation().setVisible(!myShowFlatten);
      }
    };
    final CollapseAllAction collapseAllAction = new CollapseAllAction(myTree) {
      public void update(AnActionEvent e) {
        e.getPresentation().setVisible(!myShowFlatten);
      }
    };
    final SelectAllAction selectAllAction = new SelectAllAction();
    final AnAction[] actions = new AnAction[]{directoriesAction, expandAllAction, collapseAllAction, selectAllAction};
    directoriesAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK)),
      this);
    expandAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_EXPAND_ALL)),
      myTree);
    collapseAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_COLLAPSE_ALL)),
      myTree);
    selectAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_A, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK)),
      this);
    return actions;
  }

  private class MyTreeCellRenderer extends JPanel implements TreeCellRenderer {
    private final ChangesBrowserNodeRenderer myTextRenderer;
    private final JCheckBox myCheckBox;


    public MyTreeCellRenderer() {
      super(new BorderLayout());
      myCheckBox = new JCheckBox();
      myTextRenderer = new ChangesBrowserNodeRenderer(myProject, false, myHighlightProblems);

      myCheckBox.setBackground(null);
      setBackground(null);

      if (myShowCheckboxes) {
        add(myCheckBox, BorderLayout.WEST);
      }

      add(myTextRenderer, BorderLayout.CENTER);
    }

    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      myTextRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      if (myShowCheckboxes) {
        ChangesBrowserNode node = (ChangesBrowserNode)value;

        CheckboxTree.NodeState state = getNodeStatus(node);
        myCheckBox.setSelected(state != CheckboxTree.NodeState.CLEAR);
        myCheckBox.setEnabled(state != CheckboxTree.NodeState.PARTIAL);
        revalidate();

        return this;
      }
      else {
        return myTextRenderer;
      }
    }
  }


  private CheckboxTree.NodeState getNodeStatus(ChangesBrowserNode node) {
    boolean hasIncluded = false;
    boolean hasExcluded = false;

    for (T change : getSelectedObjects(node)) {
      if (myIncludedChanges.contains(change)) {
        hasIncluded = true;
      }
      else {
        hasExcluded = true;
      }
    }

    if (hasIncluded && hasExcluded) return CheckboxTree.NodeState.PARTIAL;
    if (hasIncluded) return CheckboxTree.NodeState.FULL;
    return CheckboxTree.NodeState.CLEAR;
  }

  private class MyListCellRenderer extends JPanel implements ListCellRenderer {
    private final ColoredListCellRenderer myTextRenderer;
    public final JCheckBox myCheckbox;

    public MyListCellRenderer() {
      super(new BorderLayout());
      myCheckbox = new JCheckBox();
      myTextRenderer = new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          final FilePath path = TreeModelBuilder.getPathForObject(value);
          if (path.isDirectory()) {
            setIcon(Icons.DIRECTORY_CLOSED_ICON);
          } else {
            setIcon(path.getFileType().getIcon());
          }
          final FileStatus fileStatus;
          if (value instanceof Change) {
            fileStatus = ((Change) value).getFileStatus();
          }
          else {
            final VirtualFile virtualFile = path.getVirtualFile();
            if (virtualFile != null) {
              fileStatus = FileStatusManager.getInstance(myProject).getStatus(virtualFile);
            }
            else {
              fileStatus = FileStatus.NOT_CHANGED;
            }
          }
          append(path.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fileStatus.getColor(), null));
          final boolean applyChangeDecorator = (value instanceof Change) && myChangeDecorator != null;
          final File parentFile = path.getIOFile().getParentFile();
          if (parentFile != null) {
            final String parentPath = parentFile.getPath();
            List<Pair<String,ChangeNodeDecorator.Stress>> parts = null;
            if (applyChangeDecorator) {
              parts = myChangeDecorator.stressPartsOfFileName((Change)value, parentPath);
            }
            if (parts == null) {
              parts = Collections.singletonList(new Pair<String, ChangeNodeDecorator.Stress>(parentPath, ChangeNodeDecorator.Stress.PLAIN));
            }

            append(" (");
            for (Pair<String, ChangeNodeDecorator.Stress> part : parts) {
              append(part.getFirst(), part.getSecond().derive(SimpleTextAttributes.GRAYED_ATTRIBUTES));
            }
            append(")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
          if (applyChangeDecorator) {
            myChangeDecorator.decorate((Change) value, this, isShowFlatten());
          }
        }
      };

      myCheckbox.setBackground(null);
      setBackground(null);

      if (myShowCheckboxes) {
        add(myCheckbox, BorderLayout.WEST);
      }
      add(myTextRenderer, BorderLayout.CENTER);
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      myTextRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (myShowCheckboxes) {
        myCheckbox.setSelected(myIncludedChanges.contains(value));
        return this;
      }
      else {
        return myTextRenderer;
      }
    }
  }

  private class MyToggleSelectionAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
      toggleSelection();
    }
  }

  public class ToggleShowDirectoriesAction extends ToggleAction {
    public ToggleShowDirectoriesAction() {
      super(VcsBundle.message("changes.action.show.directories.text"),
            VcsBundle.message("changes.action.show.directories.description"),
            Icons.DIRECTORY_CLOSED_ICON);
    }

    public boolean isSelected(AnActionEvent e) {
      return !PropertiesComponent.getInstance(myProject).isTrueValue(FLATTEN_OPTION_KEY);
    }

    public void setSelected(AnActionEvent e, boolean state) {
      PropertiesComponent.getInstance(myProject).setValue(FLATTEN_OPTION_KEY, String.valueOf(!state));
      setShowFlatten(!state);
    }
  }

  private class SelectAllAction extends AnAction {
    private SelectAllAction() {
      super("Select All", "Select all items", IconLoader.getIcon("/actions/selectall.png"));
    }

    public void actionPerformed(final AnActionEvent e) {
      if (myShowFlatten) {
        final int count = myList.getModel().getSize();
        if (count > 0) {
          myList.setSelectionInterval(0, count-1);
        }
      }
      else {
        final int countTree = myTree.getRowCount();
        if (countTree > 0) {
          myTree.setSelectionInterval(0, countTree-1);
        }
      }
    }
  }

  public void select(final List<T> changes) {
    final DefaultTreeModel treeModel = (DefaultTreeModel) myTree.getModel();
    final TreeNode root = (TreeNode) treeModel.getRoot();
    final List<TreePath> treeSelection = new ArrayList<TreePath>(changes.size());
    TreeUtil.traverse(root, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        final T change = (T) ((DefaultMutableTreeNode) node).getUserObject();
        if (changes.contains(change)) {
          treeSelection.add(new TreePath(((DefaultMutableTreeNode) node).getPath()));
        }
        return true;
      }
    });
    myTree.setSelectionPaths(treeSelection.toArray(new TreePath[treeSelection.size()]));

    // list
    final ListModel model = myList.getModel();
    final int size = model.getSize();
    final List<Integer> listSelection = new ArrayList<Integer>(changes.size());
    for (int i = 0; i < size; i++) {
      final T el = (T) model.getElementAt(i);
      if (changes.contains(el)) {
        listSelection.add(i);
      }
    }
    myList.setSelectedIndices(int2int(listSelection));
  }

  private static int[] int2int(List<Integer> treeSelection) {
    final int[] toPass = new int[treeSelection.size()];
    int i = 0;
    for (Integer integer : treeSelection) {
      toPass[i] = integer;
      ++ i;
    }
    return toPass;
  }
}
