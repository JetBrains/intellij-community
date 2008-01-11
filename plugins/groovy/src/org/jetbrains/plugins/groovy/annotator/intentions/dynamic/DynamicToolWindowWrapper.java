package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.TreeTable;
import com.intellij.util.ui.treetable.TreeTableModel;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.FilterComponent;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.toolPanel.DynamicToolWindowUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.DPContainingClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.DPPropertyElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.virtual.DynamicPropertyVirtual;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.annotations.NonNls;

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2008
 */
public class DynamicToolWindowWrapper {
  private final Project myProject;
  private final JPanel myTreeTablePanel;
  private final JPanel myPanel;

  private ListTreeTableModelOnColumns myTreeTableModel;
  private String[] myColumnNames = {
      "Class and properties",
      "Type of property"
  };
  private TreeTable myTreeTable;

  public DynamicToolWindowWrapper(final Project project) {
    myProject = project;
    myPanel = new JPanel(new BorderLayout());
    myPanel.setBackground(UIUtil.getFieldForegroundColor());

    final DynamicFilterComponent filter = new DynamicFilterComponent(GroovyBundle.message("dynamic.toolwindow.property.fiter"), 10);
    filter.setBackground(UIUtil.getLabelBackground());

//    myPanel.add(new Label(GroovyBundle.message("dynamic.toolwindow.search.property")), BorderLayout.NORTH);
    myPanel.add(filter, BorderLayout.NORTH);

    myTreeTablePanel = new JPanel(new BorderLayout());
    myPanel.add(myTreeTablePanel);

    myPanel.addHierarchyListener(new HierarchyListener() {
      public void hierarchyChanged(HierarchyEvent e) {
        rebuild();
      }
    });

    DynamicPropertiesManager.getInstance(myProject).addDynamicChangeListener(new DynamicPropertyChangeListener() {
      /*
       * Change property
      */
      public void dynamicPropertyChange() {
        rebuild();
      }
    });


    myPanel.setPreferredSize(new Dimension(200, myPanel.getHeight()));
  }

  private void rebuild() {
    DefaultMutableTreeNode myRootNode;

    myRootNode = new DefaultMutableTreeNode();
    buildTree(myRootNode);

    java.util.List<TreePath> expandedPaths = null;
    if (myTreeTable != null && myTreeTable.getTree() != null) {
      expandedPaths = TreeUtil.collectExpandedPaths(myTreeTable.getTree());
    }
    rebuildTreeView(myRootNode, false);

    if (myTreeTable != null && myTreeTable.getTree() != null && expandedPaths != null) {
      TreeUtil.restoreExpandedPaths(myTreeTable.getTree(), expandedPaths);
    }
  }

  private void rebuildTreeView(DefaultMutableTreeNode root, boolean expand) {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    if (!isDynamicToolWindowShowing()) return;


    myTreeTablePanel.removeAll();

    final JScrollPane treeTable = createTable(root);

    if (expand) {
      TreeUtil.expandAll(myTreeTable.getTree());
    }

    myTreeTablePanel.add(treeTable);

    myTreeTablePanel.validate();
    myTreeTablePanel.repaint();
  }

  private DefaultMutableTreeNode buildTree(DefaultMutableTreeNode rootNode) {
    final VirtualFile currentFile = FileEditorManagerEx.getInstanceEx(myProject).getCurrentFile();

    if (currentFile == null) {
      return new DefaultMutableTreeNode();
    }
    Module module = ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(currentFile);
    if (module == null) return new DefaultMutableTreeNode();

    final Set<String> containingClasses = DynamicPropertiesManager.getInstance(myProject).getAllContainingClasses(module.getName());

    DefaultMutableTreeNode containingClassNode;
    for (String containingClassName : containingClasses) {
      containingClassNode = new DefaultMutableTreeNode(new DPContainingClassElement(containingClassName));

      final String[] properties = DynamicPropertiesManager.getInstance(myProject).findDynamicPropertiesOfClass(module.getName(), containingClassName);

      DefaultMutableTreeNode propertyTreeNode;
      for (String propertyName : properties) {
        final String propertyType = DynamicPropertiesManager.getInstance(myProject).findDynamicPropertyType(module.getName(), containingClassName, propertyName);
        propertyTreeNode = new DefaultMutableTreeNode(new DPPropertyElement(new DynamicPropertyVirtual(propertyName, containingClassName, module.getName(), propertyType)));
        containingClassNode.add(propertyTreeNode);
      }

      rootNode.add(containingClassNode);
    }
    return rootNode;
  }

  private JScrollPane createTable(MutableTreeNode myTreeRoot) {
    ColumnInfo[] columnInfos = {new ClassColumnInfo(myColumnNames[0]) {
      public Class getColumnClass() {
        return TreeTableModel.class;
      }

//      public boolean isCellEditable(TreeNode treeNode) {
//        return true;
//      }
    }, new PropertyTypeColumnInfo(myColumnNames[1])};

    myTreeTableModel = new ListTreeTableModelOnColumns(myTreeRoot, columnInfos);
    myTreeTable = new TreeTable(myTreeTableModel);
    myTreeTable.setRootVisible(false);
    myTreeTable.setAutoCreateRowSorter(true);

//    myTreeTable.setDefaultRenderer(Boolean.class, new BooleanTableCellRenderer());
    myTreeTable.getTree().setShowsRootHandles(true);
    myTreeTable.getTableHeader().setReorderingAllowed(false);

    myTreeTable.setTreeCellRenderer(new ColoredTreeCellRenderer() {
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        value = ((DefaultMutableTreeNode) value).getUserObject();

        setPaintFocusBorder(false);

        if (value != null) {

          if (value instanceof DPContainingClassElement) {
            append(((DPContainingClassElement) value).getContainingClassName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

          } else if (value instanceof DPPropertyElement) {
            final DPPropertyElement propertyElement = (DPPropertyElement) value;
            final String substringToHightlight = propertyElement.getHightlightedText();
            final String propertyName = propertyElement.getPropertyName();

            if (substringToHightlight != null) {
              final int begin = propertyName.indexOf(substringToHightlight);
              final String first = propertyName.substring(0, begin);
              append(first, SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
              final TextAttributes textAttributes = TextAttributes.ERASE_MARKER;
//              textAttributes.setEffectColor(new Color(200, 200, 200));
              textAttributes.setBackgroundColor(UIUtil.getListSelectionBackground());
              append(substringToHightlight, SimpleTextAttributes.fromTextAttributes(textAttributes));
              append(propertyName.substring(first.length() + substringToHightlight.length()), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
            } else {
              append(propertyName, SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
            }

          }
        }
      }
    });

    myTreeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTreeTable.setPreferredScrollableViewportSize(new Dimension(300, myTreeTable.getRowHeight() * 10));
    myTreeTable.getColumn(myColumnNames[0]).setPreferredWidth(200);
    myTreeTable.getColumn(myColumnNames[1]).setPreferredWidth(160);

    myTreeTable.addMouseListener(
        new MouseAdapter() {
          public void mouseClicked(MouseEvent e) {
            final Point point = e.getPoint();
            if (e.getClickCount() == 2 && myTreeTable.columnAtPoint(point) == 1) {
              myTreeTable.editCellAt(myTreeTable.rowAtPoint(point), myTreeTable.columnAtPoint(point), e);
            }
          }
        }
    );

    JScrollPane scrollpane = ScrollPaneFactory.createScrollPane(myTreeTable);

    scrollpane.setPreferredSize(new Dimension(600, 400));
    return scrollpane;
  }

  protected boolean isDynamicToolWindowShowing() {
    ToolWindowManager windowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow = windowManager.getToolWindow(DynamicToolWindowUtil.DYNAMIC_TOOLWINDOW_ID);
    return toolWindow != null && toolWindow.isVisible();
  }

  public JPanel getComponent() {
    return myPanel;
  }

  class PropertyNameColumnInfo extends ColumnInfo<DefaultMutableTreeNode, String> {
    public PropertyNameColumnInfo(String name) {
      super(name);
    }

    public String valueOf(DefaultMutableTreeNode treeNode) {
      Object userObject = treeNode.getUserObject();
      if (userObject instanceof DPContainingClassElement) {
        return ((DPContainingClassElement) userObject).getContainingClassName();

      } else if (userObject instanceof DPPropertyElement) {
        return ((DPPropertyElement) userObject).getPropertyName();
      }
      return null;
    }

    public boolean isCellEditable(DefaultMutableTreeNode defaultMutableTreeNode) {
      return true;
    }
  }

  class PropertyTypeColumnInfo extends ColumnInfo<DefaultMutableTreeNode, String> {
    public PropertyTypeColumnInfo(String name) {
      super(name);
    }

    public boolean isCellEditable(DefaultMutableTreeNode defaultMutableTreeNode) {
      return true;
    }

    public String valueOf(DefaultMutableTreeNode treeNode) {
      Object userObject = treeNode.getUserObject();
      if (userObject instanceof DPContainingClassElement) {
        return "";

      } else if (userObject instanceof DPPropertyElement) {
        return ((DPPropertyElement) userObject).getPropertyType();
      }
      return null;
    }
  }

  class ClassColumnInfo extends ColumnInfo<DefaultMutableTreeNode, String> {
    public ClassColumnInfo(String name) {
      super(name);
    }

    public boolean isCellEditable(DefaultMutableTreeNode defaultMutableTreeNode) {
      return false;
    }

    public String valueOf(DefaultMutableTreeNode treeNode) {
      Object userObject = treeNode.getUserObject();
      if (userObject instanceof DPContainingClassElement) {
        return ((DPContainingClassElement) userObject).getContainingClassName();

      } else {
        return "";
      }
//      return null;
    }
  }

  class DynamicFilterComponent extends FilterComponent {
    public DynamicFilterComponent(@NonNls String propertyName, int historySize) {
      super(propertyName, historySize);
    }

    public void filter() {
      DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
      buildTree(rootNode);

      String filterText;

      DefaultMutableTreeNode newContainingClassNode;
      TreeNode containingClassNode = rootNode.getFirstChild();
      while (containingClassNode != null) {
        if (!(containingClassNode instanceof DefaultMutableTreeNode)) break;

        TreeNode child = ((DefaultMutableTreeNode) containingClassNode).getFirstChild();
        DefaultMutableTreeNode newChild;
        while (child != null) {
          if (!(child instanceof DefaultMutableTreeNode)) break;

          final Object userObject = ((DefaultMutableTreeNode) child).getUserObject();
          if (!(userObject instanceof DPPropertyElement)) break;

          filterText = getFilter();
          if (filterText == null) break;

          final String propertyName = ((DPPropertyElement) userObject).getPropertyName();

          if (propertyName == null || "".equals(filterText)) break;

          newChild = ((DefaultMutableTreeNode) child).getNextSibling();

          if (!propertyName.contains(filterText)) {
            final TreeNode parent = child.getParent();

            if (!(parent instanceof DefaultMutableTreeNode)) break;

            ((DefaultMutableTreeNode) parent).remove(((DefaultMutableTreeNode) child));
          } else {
            ((DPPropertyElement) userObject).setHightlightedText(filterText);
          }

          child = newChild;
        }

        newContainingClassNode = ((DefaultMutableTreeNode) containingClassNode).getNextSibling();

        if (containingClassNode.getChildCount() == 0) {
          final TreeNode parent = containingClassNode.getParent();

          if (!(parent instanceof DefaultMutableTreeNode)) break;
          ((DefaultMutableTreeNode) parent).remove(((DefaultMutableTreeNode) containingClassNode));
        }

        containingClassNode = newContainingClassNode;
      }

      rebuildTreeView(rootNode, true);
    }
  }
}