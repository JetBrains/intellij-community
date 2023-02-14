// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.PlusMinus;
import com.intellij.openapi.vcs.changes.ui.VirtualFileListCellRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.treeWithCheckedNodes.SelectionManager;
import com.intellij.util.treeWithCheckedNodes.TreeNodeState;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
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
import java.util.List;
import java.util.*;

public class VcsStructureChooser extends DialogWrapper {
  private static final int MAX_FOLDERS = 100;
  public static final Border BORDER = IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.LEFT);
  private static final @NonNls String VCS_STRUCTURE_CHOOSER_KEY = "git4idea.history.wholeTree.VcsStructureChooser";

  private final @NotNull Project myProject;
  private final @NotNull List<VirtualFile> myRoots;
  private final @NotNull Map<VirtualFile, @Nls String> myModulesSet;
  private final @NotNull Set<VirtualFile> mySelectedFiles = new HashSet<>();

  private final @NotNull SelectionManager mySelectionManager;

  private Tree myTree;

  public VcsStructureChooser(@NotNull Project project,
                             @NlsContexts.DialogTitle @NotNull String title,
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

  private @NotNull Map<VirtualFile, @Nls String> calculateModules(@NotNull List<? extends VirtualFile> roots) {
    Map<VirtualFile, @Nls String> result = new HashMap<>();

    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    // assertion for read access inside
    Module[] modules = ReadAction.compute(() -> moduleManager.getModules());

    TreeSet<VirtualFile> checkSet = new TreeSet<>(FilePathComparator.getInstance());
    checkSet.addAll(roots);
    for (Module module : modules) {
      if (ModuleType.isInternal(module)) continue;
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

  public @NotNull Collection<VirtualFile> getSelectedFiles() {
    return mySelectedFiles;
  }

  private void checkEmpty() {
    setOKActionEnabled(!mySelectedFiles.isEmpty());
  }

  @Override
  protected @NotNull String getDimensionServiceKey() {
    return VCS_STRUCTURE_CHOOSER_KEY;
  }

  @Override
  public @NotNull JComponent getPreferredFocusedComponent() {
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
      }) {
        @Override
        protected Comparator<? super NodeDescriptor<?>> getFileComparator() {
          return (o1, o2) -> {
            if (o1 instanceof FileNodeDescriptor && o2 instanceof FileNodeDescriptor) {
              VirtualFile f1 = ((FileNodeDescriptor)o1).getElement().getFile();
              VirtualFile f2 = ((FileNodeDescriptor)o2).getElement().getFile();

              boolean isDir1 = f1.isDirectory();
              boolean isDir2 = f2.isDirectory();
              if (isDir1 != isDir2) return isDir1 ? -1 : 1;

              return f1.getPath().compareToIgnoreCase(f2.getPath());
            }
            return o1.getIndex() - o2.getIndex();
          };
        }
      };

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
      @Override
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

    mySelectionManager.setSelectionChangeListener(new PlusMinus<>() {
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
          HtmlChunk.Element errorText =
            HtmlChunk.text("(" + VcsLogBundle.message("vcs.log.filters.structure.max.selected.error.message", MAX_FOLDERS) + ")")
              .wrapWith(HtmlChunk.tag("font").attr("color", "red"));
          selectedLabel.setText(new HtmlBuilder()
                                  .appendRaw((VcsLogBundle.message("vcs.log.filters.structure.label", errorText)))
                                  .wrapWith(HtmlChunk.html())
                                  .toString());
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

  private @NotNull DefaultMutableTreeNode getTreeRoot() {
    return (DefaultMutableTreeNode)myTree.getModel().getRoot();
  }

  private static @Nullable VirtualFile getFile(@NotNull Object node) {
    if (!(((DefaultMutableTreeNode)node).getUserObject() instanceof FileNodeDescriptor descriptor)) return null;
    if (descriptor.getElement().getFile() == null) return null;
    return descriptor.getElement().getFile();
  }

  private static final class MyCheckboxTreeCellRenderer extends JPanel implements TreeCellRenderer {
    private final @NotNull WithModulesListCellRenderer myTextRenderer;
    public final @NotNull JCheckBox myCheckbox;
    private final @NotNull SelectionManager mySelectionManager;
    private final @NotNull Map<VirtualFile, @Nls String> myModulesSet;
    private final @NotNull Collection<VirtualFile> myRoots;
    private final @NotNull ColoredTreeCellRenderer myColoredRenderer;
    private final @NotNull JLabel myEmpty;
    private final @NotNull JList myFictive;

    private MyCheckboxTreeCellRenderer(@NotNull SelectionManager selectionManager,
                                       @NotNull Map<VirtualFile, @Nls String> modulesSet,
                                       @NotNull Project project,
                                       @NotNull JTree tree,
                                       @NotNull Collection<VirtualFile> roots) {
      super(new BorderLayout());
      mySelectionManager = selectionManager;
      myModulesSet = modulesSet;
      myRoots = roots;
      setBackground(RenderingUtil.getBackground(tree));
      myColoredRenderer = new ColoredTreeCellRenderer() {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
          //noinspection HardCodedStringLiteral
          append(value.toString());
        }
      };
      myFictive = new JBList();
      myFictive.setBackground(RenderingUtil.getBackground(tree));
      myFictive.setSelectionBackground(UIUtil.getListSelectionBackground(true));
      myFictive.setSelectionForeground(NamedColorUtil.getListSelectionForeground(true));

      myTextRenderer = new WithModulesListCellRenderer(project, myModulesSet) {
        @Override
        protected void putParentPath(Object value, FilePath path, FilePath self) {
          if (myRoots.contains(self.getVirtualFile())) {
            super.putParentPath(value, path, self);
          }
        }
      };
      myTextRenderer.setBackground(RenderingUtil.getBackground(tree));

      myCheckbox = new JCheckBox();
      myCheckbox.setBackground(RenderingUtil.getBackground(tree));
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
    private static final @NotNull MyNodeConverter ourInstance = new MyNodeConverter();

    public static @NotNull MyNodeConverter getInstance() {
      return ourInstance;
    }

    @Override
    public VirtualFile convert(DefaultMutableTreeNode o) {
      return ((FileNodeDescriptor)o.getUserObject()).getElement().getFile();
    }
  }

  private static class WithModulesListCellRenderer extends VirtualFileListCellRenderer {
    private final @NotNull Map<VirtualFile, @Nls String> myModules;

    private WithModulesListCellRenderer(@NotNull Project project, @NotNull Map<VirtualFile, @Nls String> modules) {
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
          setIcon(PlatformIcons.FOLDER_ICON);
        }
        else {
          setIcon(VcsUtil.getIcon(myProject, path));
        }
      }
    }

    @Override
    protected void putParentPathImpl(@NotNull Object value, @NotNull String parentPath, @NotNull FilePath self) {
      append(self.getPath(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }
}
