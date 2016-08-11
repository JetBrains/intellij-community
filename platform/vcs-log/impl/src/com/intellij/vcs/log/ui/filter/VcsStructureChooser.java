/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.VirtualFileListCellRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
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
  @NotNull private final List<VirtualFile> myRoots;
  @NotNull private final Map<VirtualFile, String> myModulesSet;
  @NotNull private final Set<VirtualFile> mySelectedFiles = ContainerUtil.newHashSet();

  @NotNull private final SelectionManager mySelectionManager;

  private Tree myTree;

  public VcsStructureChooser(@NotNull Project project,
                             @NotNull String title,
                             @NotNull Collection<VirtualFile> initialSelection,
                             @NotNull List<VirtualFile> roots) {
    super(project, true);
    setTitle(title);
    myProject = project;
    myRoots = roots;
    mySelectionManager = new SelectionManager(MAX_FOLDERS, 500, MyNodeConverter.getInstance());
    myModulesSet = calculateModules(roots);

    init();

    mySelectionManager.setSelection(initialSelection);

    checkEmpty();
  }

  @NotNull
  private Map<VirtualFile, String> calculateModules(@NotNull List<VirtualFile> roots) {
    Map<VirtualFile, String> result = ContainerUtil.newHashMap();

    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    // assertion for read access inside
    Module[] modules = ApplicationManager.getApplication().runReadAction(new Computable<Module[]>() {
      public Module[] compute() {
        return moduleManager.getModules();
      }
    });

    TreeSet<VirtualFile> checkSet = new TreeSet<>(FilePathComparator.getInstance());
    checkSet.addAll(roots);
    for (Module module : modules) {
      VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      for (VirtualFile file : files) {
        VirtualFile floor = checkSet.floor(file);
        if (floor != null) {
          result.put(file, module.getName());
        }
      }
    }
    return result;
  }

  @NotNull
  public Collection<VirtualFile> getSelectedFiles() {
    return mySelectedFiles;
  }

  private void checkEmpty() {
    setOKActionEnabled(!mySelectedFiles.isEmpty());
  }

  @Override
  @NotNull
  protected String getDimensionServiceKey() {
    return VCS_STRUCTURE_CHOOSER_KEY;
  }

  @Override
  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  @Override
  protected JComponent createCenterPanel() {
    myTree = new Tree();
    myTree.setBorder(BORDER);
    myTree.setShowsRootHandles(true);
    myTree.setRootVisible(false);
    myTree.setExpandableItemsEnabled(false);

    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, true, false, true) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!super.isFileVisible(file, showHiddenFiles)) return false;
        if (myRoots.contains(file)) return false;
        ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
        return !changeListManager.isIgnoredFile(file) && !changeListManager.isUnversioned(file);
      }
    };
    descriptor.withRoots(new ArrayList<>(myRoots)).withShowHiddenFiles(true).withHideIgnored(true);
    final MyCheckboxTreeCellRenderer cellRenderer =
      new MyCheckboxTreeCellRenderer(mySelectionManager, myModulesSet, myProject, myTree, myRoots);
    FileSystemTreeImpl fileSystemTree =
      new FileSystemTreeImpl(myProject, descriptor, myTree, cellRenderer, null, o -> {
        DefaultMutableTreeNode lastPathComponent = ((DefaultMutableTreeNode)o.getLastPathComponent());
        Object uo = lastPathComponent.getUserObject();
        if (uo instanceof FileNodeDescriptor) {
          VirtualFile file = ((FileNodeDescriptor)uo).getElement().getFile();
          String module = myModulesSet.get(file);
          if (module != null) return module;
          return file == null ? "" : file.getName();
        }
        return o.toString();
      });

    fileSystemTree.getTreeBuilder().getUi().setNodeDescriptorComparator((o1, o2) -> {
      if (o1 instanceof FileNodeDescriptor && o2 instanceof FileNodeDescriptor) {
        VirtualFile f1 = ((FileNodeDescriptor)o1).getElement().getFile();
        VirtualFile f2 = ((FileNodeDescriptor)o2).getElement().getFile();

        boolean isDir1 = f1.isDirectory();
        boolean isDir2 = f2.isDirectory();
        if (isDir1 != isDir2) return isDir1 ? -1 : 1;

        return f1.getPath().compareToIgnoreCase(f2.getPath());
      }
      return o1.getIndex() - o2.getIndex();
    });

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        int row = myTree.getRowForLocation(e.getX(), e.getY());
        if (row < 0) return false;
        Object o = myTree.getPathForRow(row).getLastPathComponent();
        if (getTreeRoot() == o || getFile(o) == null) return false;

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
            Object o = path.getLastPathComponent();
            if (getTreeRoot() == o || getFile(o) == null) return;
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
    final JLabel selectedLabel = new JLabel("");
    selectedLabel.setBorder(JBUI.Borders.empty(2, 0));
    panel.add(selectedLabel, BorderLayout.SOUTH);

    mySelectionManager.setSelectionChangeListener(new PlusMinus<VirtualFile>() {
      @Override
      public void plus(VirtualFile virtualFile) {
        mySelectedFiles.add(virtualFile);
        recalculateErrorText();
      }

      private void recalculateErrorText() {
        checkEmpty();
        if (mySelectionManager.canAddSelection()) {
          selectedLabel.setText("");
        }
        else {
          selectedLabel.setText(CAN_NOT_ADD_TEXT);
        }
        selectedLabel.revalidate();
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

  @NotNull
  private DefaultMutableTreeNode getTreeRoot() {
    return (DefaultMutableTreeNode)myTree.getModel().getRoot();
  }

  @Nullable
  private static VirtualFile getFile(@NotNull Object node) {
    if (!(((DefaultMutableTreeNode)node).getUserObject() instanceof FileNodeDescriptor)) return null;
    FileNodeDescriptor descriptor = (FileNodeDescriptor)((DefaultMutableTreeNode)node).getUserObject();
    if (descriptor.getElement().getFile() == null) return null;
    return descriptor.getElement().getFile();
  }

  private static class MyCheckboxTreeCellRenderer extends JPanel implements TreeCellRenderer {
    @NotNull private final WithModulesListCellRenderer myTextRenderer;
    @NotNull public final JCheckBox myCheckbox;
    @NotNull private final SelectionManager mySelectionManager;
    @NotNull private final Map<VirtualFile, String> myModulesSet;
    @NotNull private final Collection<VirtualFile> myRoots;
    @NotNull private final ColoredTreeCellRenderer myColoredRenderer;
    @NotNull private final JLabel myEmpty;
    @NotNull private final JList myFictive;

    private MyCheckboxTreeCellRenderer(@NotNull SelectionManager selectionManager,
                                       @NotNull Map<VirtualFile, String> modulesSet,
                                       @NotNull Project project,
                                       @NotNull JTree tree,
                                       @NotNull Collection<VirtualFile> roots) {
      super(new BorderLayout());
      mySelectionManager = selectionManager;
      myModulesSet = modulesSet;
      myRoots = roots;
      setBackground(tree.getBackground());
      myColoredRenderer = new ColoredTreeCellRenderer() {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree,
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
      if (value == null) return myEmpty;
      VirtualFile file = getFile(value);
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      if (file == null) {
        Object uo = node.getUserObject();
        if (uo instanceof String) {
          myColoredRenderer.getTreeCellRendererComponent(tree, node, selected, expanded, leaf, row, hasFocus);
          return myColoredRenderer;
        }
        return myEmpty;
      }
      myCheckbox.setVisible(true);
      TreeNodeState state = mySelectionManager.getState(node);
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
    @NotNull private final static MyNodeConverter ourInstance = new MyNodeConverter();

    @NotNull
    public static MyNodeConverter getInstance() {
      return ourInstance;
    }

    @Override
    public VirtualFile convert(DefaultMutableTreeNode o) {
      return ((FileNodeDescriptor)o.getUserObject()).getElement().getFile();
    }
  }

  private static class WithModulesListCellRenderer extends VirtualFileListCellRenderer {
    @NotNull private final Map<VirtualFile, String> myModules;

    private WithModulesListCellRenderer(@NotNull Project project, @NotNull Map<VirtualFile, String> modules) {
      super(project, true);
      myModules = modules;
    }

    @Override
    protected String getName(@NotNull FilePath path) {
      String module = myModules.get(path.getVirtualFile());
      if (module != null) {
        return module;
      }
      return super.getName(path);
    }

    @Override
    protected void renderIcon(@NotNull FilePath path) {
      String module = myModules.get(path.getVirtualFile());
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
    protected void putParentPathImpl(@NotNull Object value, @NotNull String parentPath, @NotNull FilePath self) {
      append(self.getPath(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }
}
