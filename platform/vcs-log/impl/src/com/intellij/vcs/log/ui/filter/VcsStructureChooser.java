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
package com.intellij.vcs.log.ui.filter;

import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.VirtualFileListCellRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.PlusMinus;
import com.intellij.util.TreeNodeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.treeWithCheckedNodes.SelectionManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author irengrig
 *         Date: 2/3/11
 *         Time: 12:04 PM
 */
public class VcsStructureChooser extends DialogWrapper {
  private final static int MAX_FOLDERS = 100;
  public static final Border BORDER = IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.LEFT);
  public static final String CAN_NOT_ADD_TEXT =
    "<html>Selected: <font color=red>(You have added " + MAX_FOLDERS + " elements. No more is allowed.)</font></html>";
  private static final String VCS_STRUCTURE_CHOOSER_KEY = "git4idea.history.wholeTree.VcsStructureChooser";

  @NotNull private final Project myProject;
  @NotNull private final List<VirtualFile> myInitialRoots;
  @NotNull private final Map<VirtualFile, String> myModulesSet = ContainerUtil.newHashMap();
  @NotNull private final Set<VirtualFile> mySelectedFiles = ContainerUtil.newHashSet();

  @NotNull private final SelectionManager mySelectionManager;

  private Set<VirtualFile> myRoots;
  private JLabel mySelectedLabel;
  private DefaultMutableTreeNode myRoot;
  private Tree myTree;

  public VcsStructureChooser(@NotNull Project project,
                             String title,
                             Collection<VirtualFile> initialSelection,
                             @NotNull List<VirtualFile> initialRoots) {
    super(project, true);
    setTitle(title);
    myProject = project;
    myInitialRoots = initialRoots;
    mySelectionManager = new SelectionManager(MAX_FOLDERS, 500, MyNodeConverter.getInstance());

    init();

    mySelectionManager.setSelection(initialSelection);

    checkEmpty();
  }

  private void calculateRoots() {
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    // assertion for read access inside
    final Module[] modules = ApplicationManager.getApplication().runReadAction(new Computable<Module[]>() {
      public Module[] compute() {
        return moduleManager.getModules();
      }
    });

    final TreeSet<VirtualFile> checkSet = new TreeSet<VirtualFile>(FilePathComparator.getInstance());
    myRoots = new HashSet<VirtualFile>();
    myRoots.addAll(myInitialRoots);
    checkSet.addAll(myInitialRoots);
    for (Module module : modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      for (VirtualFile file : files) {
        final VirtualFile floor = checkSet.floor(file);
        if (floor != null) {
          myModulesSet.put(file, module.getName());
          myRoots.add(file);
        }
      }
    }
  }

  @NotNull
  public Collection<VirtualFile> getSelectedFiles() {
    return mySelectedFiles;
  }

  private void checkEmpty() {
    setOKActionEnabled(!mySelectedFiles.isEmpty());
  }

  @Override
  protected String getDimensionServiceKey() {
    return VCS_STRUCTURE_CHOOSER_KEY;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  @Override
  protected JComponent createCenterPanel() {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor();
    calculateRoots();
    final ArrayList<VirtualFile> list = new ArrayList<VirtualFile>(myRoots);
    final Comparator<VirtualFile> comparator = new Comparator<VirtualFile>() {
      @Override
      public int compare(VirtualFile o1, VirtualFile o2) {
        final boolean isDir1 = o1.isDirectory();
        final boolean isDir2 = o2.isDirectory();
        if (isDir1 != isDir2) return isDir1 ? -1 : 1;

        final String module1 = myModulesSet.get(o1);
        final String path1 = module1 != null ? module1 : o1.getPath();
        final String module2 = myModulesSet.get(o2);
        final String path2 = module2 != null ? module2 : o2.getPath();
        return path1.compareToIgnoreCase(path2);
      }
    };
    descriptor.setRoots(list);
    myTree = new Tree();
    myTree.setBorder(BORDER);
    myTree.setShowsRootHandles(true);
    myTree.setRootVisible(true);
    myTree.getExpandableItemsHandler().setEnabled(false);
    final MyCheckboxTreeCellRenderer cellRenderer =
      new MyCheckboxTreeCellRenderer(mySelectionManager, myModulesSet, myProject, myTree, myRoots);
    final FileSystemTreeImpl fileSystemTree =
      new FileSystemTreeImpl(myProject, descriptor, myTree, cellRenderer, null, new Convertor<TreePath, String>() {
        @Override
        public String convert(TreePath o) {
          final DefaultMutableTreeNode lastPathComponent = ((DefaultMutableTreeNode)o.getLastPathComponent());
          final Object uo = lastPathComponent.getUserObject();
          if (uo instanceof FileNodeDescriptor) {
            final VirtualFile file = ((FileNodeDescriptor)uo).getElement().getFile();
            final String module = myModulesSet.get(file);
            if (module != null) return module;
            return file == null ? "" : file.getName();
          }
          return o.toString();
        }
      });
    final AbstractTreeUi ui = fileSystemTree.getTreeBuilder().getUi();
    ui.setNodeDescriptorComparator(new Comparator<NodeDescriptor>() {
      @Override
      public int compare(NodeDescriptor o1, NodeDescriptor o2) {
        if (o1 instanceof FileNodeDescriptor && o2 instanceof FileNodeDescriptor) {
          final VirtualFile f1 = ((FileNodeDescriptor)o1).getElement().getFile();
          final VirtualFile f2 = ((FileNodeDescriptor)o2).getElement().getFile();
          return comparator.compare(f1, f2);
        }
        return o1.getIndex() - o2.getIndex();
      }
    });
    myRoot = (DefaultMutableTreeNode)myTree.getModel().getRoot();

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        int row = myTree.getRowForLocation(e.getX(), e.getY());
        if (row < 0) return false;
        final Object o = myTree.getPathForRow(row).getLastPathComponent();
        if (myRoot == o || getFile(o) == null) return false;

        Rectangle rowBounds = myTree.getRowBounds(row);
        cellRenderer.setBounds(rowBounds);
        Rectangle checkBounds = cellRenderer.myCheckbox.getBounds();
        checkBounds.setLocation(rowBounds.getLocation());

        if (checkBounds.height == 0) checkBounds.height = rowBounds.height;

        if (checkBounds.contains(e.getPoint())) {
          mySelectionManager.toggleSelection((DefaultMutableTreeNode)o);
          myTree.revalidate();
          myTree.repaint();
        }
        return true;
      }
    }.installOn(myTree);

    myTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          TreePath[] paths = myTree.getSelectionPaths();
          if (paths == null) return;
          for (TreePath path : paths) {
            if (path == null) continue;
            final Object o = path.getLastPathComponent();
            if (myRoot == o || getFile(o) == null) return;
            mySelectionManager.toggleSelection((DefaultMutableTreeNode)o);
          }

          myTree.revalidate();
          myTree.repaint();
          e.consume();
        }
      }
    });

    JBPanel panel = new JBPanel(new BorderLayout());
    panel.add(new JBScrollPane(fileSystemTree.getTree()), BorderLayout.CENTER);
    mySelectedLabel = new JLabel("");
    mySelectedLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
    panel.add(mySelectedLabel, BorderLayout.SOUTH);

    mySelectionManager.setSelectionChangeListener(new PlusMinus<VirtualFile>() {
      @Override
      public void plus(VirtualFile virtualFile) {
        mySelectedFiles.add(virtualFile);
        recalculateErrorText();
      }

      private void recalculateErrorText() {
        checkEmpty();
        if (mySelectionManager.canAddSelection()) {
          mySelectedLabel.setText("");
        }
        else {
          mySelectedLabel.setText(CAN_NOT_ADD_TEXT);
        }
        mySelectedLabel.revalidate();
      }

      @Override
      public void minus(VirtualFile virtualFile) {
        mySelectedFiles.remove(virtualFile);
        recalculateErrorText();
      }
    });
    panel.setPreferredSize(JBUI.size(400, 300));
    return panel;
  }

  @Nullable
  private static VirtualFile getFile(final Object node) {
    if (!(((DefaultMutableTreeNode)node).getUserObject() instanceof FileNodeDescriptor)) return null;
    final FileNodeDescriptor descriptor = (FileNodeDescriptor)((DefaultMutableTreeNode)node).getUserObject();
    if (descriptor.getElement().getFile() == null) return null;
    return descriptor.getElement().getFile();
  }

  private static class MyCheckboxTreeCellRenderer extends JPanel implements TreeCellRenderer {
    private final WithModulesListCellRenderer myTextRenderer;
    public final JCheckBox myCheckbox;
    private final SelectionManager mySelectionManager;
    private final Map<VirtualFile, String> myModulesSet;
    private final Collection<VirtualFile> myRoots;
    private final ColoredTreeCellRenderer myColoredRenderer;
    private final JLabel myEmpty;
    private final JList myFictive;

    private MyCheckboxTreeCellRenderer(final SelectionManager selectionManager,
                                       Map<VirtualFile, String> modulesSet,
                                       final Project project,
                                       final JTree tree,
                                       final Collection<VirtualFile> roots) {
      super(new BorderLayout());
      mySelectionManager = selectionManager;
      myModulesSet = modulesSet;
      myRoots = roots;
      setBackground(tree.getBackground());
      myColoredRenderer = new ColoredTreeCellRenderer() {
        @Override
        public void customizeCellRenderer(JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
          append(value.toString());
        }
      };
      myFictive = new JBList();
      myFictive.setBackground(tree.getBackground());
      myFictive.setSelectionBackground(UIUtil.getListSelectionBackground());
      myFictive.setSelectionForeground(UIUtil.getListSelectionForeground());

      myTextRenderer = new WithModulesListCellRenderer(project, myModulesSet) {
        @Override
        protected void putParentPath(Object value, FilePath path, FilePath self) {
          if (myRoots.contains(self.getVirtualFile())) {
            super.putParentPath(value, path, self);
          }
        }
      };
      myTextRenderer.setBackground(tree.getBackground());

      myCheckbox = new JCheckBox();
      myCheckbox.setBackground(tree.getBackground());
      myEmpty = new JLabel("");

      add(myCheckbox, BorderLayout.WEST);
      add(myTextRenderer, BorderLayout.CENTER);
      myCheckbox.setVisible(true);
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      invalidate();
      final VirtualFile file = getFile(value);
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      if (file == null) {
        if (value instanceof DefaultMutableTreeNode) {
          final Object uo = node.getUserObject();
          if (uo instanceof String) {
            myColoredRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            return myColoredRenderer;
          }
        }
        return myEmpty;
      }
      myCheckbox.setVisible(true);
      final TreeNodeState state = mySelectionManager.getState(node);
      myCheckbox.setEnabled(TreeNodeState.CLEAR.equals(state) || TreeNodeState.SELECTED.equals(state));
      myCheckbox.setSelected(!TreeNodeState.CLEAR.equals(state));
      myCheckbox.setOpaque(false);
      myCheckbox.setBackground(null);
      setBackground(null);
      myTextRenderer.getListCellRendererComponent(myFictive, file, 0, selected, hasFocus);
      revalidate();
      return this;
    }
  }

  private static class MyNodeConverter implements Convertor<DefaultMutableTreeNode, VirtualFile> {
    private final static MyNodeConverter ourInstance = new MyNodeConverter();

    public static MyNodeConverter getInstance() {
      return ourInstance;
    }

    @Override
    public VirtualFile convert(DefaultMutableTreeNode o) {
      return ((FileNodeDescriptor)o.getUserObject()).getElement().getFile();
    }
  }

  private static class WithModulesListCellRenderer extends VirtualFileListCellRenderer {
    private final Map<VirtualFile, String> myModules;

    private WithModulesListCellRenderer(Project project, final Map<VirtualFile, String> modules) {
      super(project, true);
      myModules = modules;
    }

    @Override
    protected String getName(FilePath path) {
      final String module = myModules.get(path.getVirtualFile());
      if (module != null) {
        return module;
      }
      return super.getName(path);
    }

    @Override
    protected void renderIcon(FilePath path) {
      final String module = myModules.get(path.getVirtualFile());
      if (module != null) {
        setIcon(PlatformIcons.CONTENT_ROOT_ICON_CLOSED);
      }
      else {
        if (path.isDirectory()) {
          setIcon(PlatformIcons.DIRECTORY_CLOSED_ICON);
        }
        else {
          setIcon(path.getFileType().getIcon());
        }
      }
    }

    @Override
    protected void putParentPathImpl(Object value, String parentPath, FilePath self) {
      append(self.getPath(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }
}
