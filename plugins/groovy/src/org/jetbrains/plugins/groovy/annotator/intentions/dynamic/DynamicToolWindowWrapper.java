package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.Disposable;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.TreeTable;
import com.intellij.util.ui.treetable.TreeTableModel;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.IJSwingUtilities;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.FilterComponent;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.tree.TreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.toolPanel.DynamicToolWindowUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.*;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.virtual.DynamicPropertyVirtual;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2008
 */
public class DynamicToolWindowWrapper {
  private final Project myProject;
  private final ToolWindow myDynamicToolWindow;
  private JPanel myTreeTablePanel;
  private JPanel myPanel;

  private DynamicTreeViewState myState = new DynamicTreeViewState();

  private ListTreeTableModelOnColumns myTreeTableModel;
  private String[] myColumnNames = {
      "Class and properties",
      "Type of property"
  };
  private TreeTable myTreeTable;

  private static final Key<DynamicTreeViewState> DYNAMIC_TOOLWINDOW_STATE_KEY = Key.create("DYNAMIC_TOOLWINDOW_STATE");

  public DynamicToolWindowWrapper(final Project project, ToolWindow dynamicToolWindow) {
    myProject = project;
    myDynamicToolWindow = dynamicToolWindow;

    buildWholePanel();

    DynamicPropertiesManager.getInstance(myProject).addDynamicChangeListener(new DynamicPropertyChangeListener() {
      public void dynamicPropertyChange() {
        storeState();
        rebuildTreePanel();
        restoreState();
      }
    });

    Disposer.register(myDynamicToolWindow.getContentManager(), new Disposable() {
      public void dispose() {
        storeState();
      }
    });
  }

  private void buildWholePanel() {
    myPanel = new JPanel(new BorderLayout());
    myPanel.setBackground(UIUtil.getFieldForegroundColor());

    final DynamicFilterComponent filter = new DynamicFilterComponent(GroovyBundle.message("dynamic.toolwindow.property.fiter"), 10);
    filter.setBackground(UIUtil.getLabelBackground());

    myPanel.add(new Label(GroovyBundle.message("dynamic.toolwindow.search.property")), BorderLayout.NORTH);
    myPanel.add(filter, BorderLayout.NORTH);

    myTreeTablePanel = new JPanel(new BorderLayout());
    rebuildTreePanel();

    myPanel.add(myTreeTablePanel);
    myPanel.setPreferredSize(new Dimension(200, myPanel.getHeight()));

    myPanel.revalidate();
  }

  private void rebuildTreePanel() {

//    storeState();
    if (!isDynamicToolWindowShowing()) return;

    DefaultMutableTreeNode myRootNode = new DefaultMutableTreeNode();
    buildTree(myRootNode);

    rebuildTreeView(myRootNode, false);

//    restoreState();
  }

  private void rebuildTreeView(DefaultMutableTreeNode root, boolean expandAll) {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    if (!isDynamicToolWindowShowing()) return;

//    storeState();
    myTreeTablePanel.removeAll();

    final JScrollPane treeTable = createTable(root);


    if (expandAll) {
      TreeUtil.expandAll(myTreeTable.getTree());
    }

    boolean hadFocus = IJSwingUtilities.hasFocus2(myTreeTable);
    if (hadFocus) {
      JComponent focusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myTreeTable);
      if (focusedComponent != null) {
        focusedComponent.requestFocus();
      }
    }

    myTreeTablePanel.add(treeTable);
  }

  private DefaultMutableTreeNode buildTree(DefaultMutableTreeNode rootNode) {
    final Module module = getModule();
    if (module == null) return new DefaultMutableTreeNode();

    final Set<String> containingClasses = DynamicPropertiesManager.getInstance(myProject).getAllContainingClasses(module.getName());

    DefaultMutableTreeNode containingClassNode;
    for (String containingClassName : containingClasses) {
      containingClassNode = new DefaultMutableTreeNode(new DPContainingClassElement(containingClassName));

      final String[] properties = DynamicPropertiesManager.getInstance(myProject).findDynamicPropertiesOfClass(module.getName(), containingClassName);

      if (properties.length == 0) continue;

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
    ColumnInfo[] columnInfos = {new ClassColumnInfo(myColumnNames[0]), new PropertyTypeColumnInfo(myColumnNames[1])};

    myTreeTableModel = new ListTreeTableModelOnColumns(myTreeRoot, columnInfos);
    myTreeTable = new TreeTable(myTreeTableModel);
    myTreeTable.setRootVisible(false);

    myTreeTable.registerKeyboardAction(
        new ActionListener() {
          public void actionPerformed(ActionEvent event) {
            final List<TreePath> expandedTreePathList = TreeUtil.collectExpandedPaths(myTreeTable.getTree());
            final int[] rows = myTreeTable.getSelectedRows();
//            for (int row : rows) {
            final TreePath selectionPath = myTreeTable.getTree().getAnchorSelectionPath();

            //class
            final TreePath parent = selectionPath.getParentPath();

            final Module module = getModule();
            if (parent.getParentPath() == null) {
              //selectionPath is class

              final Object containingClassRow = parent.getLastPathComponent();

              if (!(containingClassRow instanceof DefaultMutableTreeNode)) return;
              final Object containingClass = ((DefaultMutableTreeNode) containingClassRow).getUserObject();

              if (module == null) return;
              if (!(containingClass instanceof DPContainingClassElement)) return;

              DynamicPropertiesManager.getInstance(myProject).removeDynamicPropertiesOfClass(module.getName(), ((DPContainingClassElement) containingClass).getContainingClassName());
            } else {
              //selectionPath is property
              final Object containingClass = parent.getLastPathComponent();
              final Object property = selectionPath.getLastPathComponent();

              if (!(containingClass instanceof DefaultMutableTreeNode)) return;
              if (!(property instanceof DefaultMutableTreeNode)) return;

              final Object classElement = ((DefaultMutableTreeNode) containingClass).getUserObject();
              final Object propertyElement = ((DefaultMutableTreeNode) property).getUserObject();

              if (!(classElement instanceof DPContainingClassElement)) return;
              if (!(propertyElement instanceof DPPropertyElement)) return;

              final String containingClassName = ((DPContainingClassElement) classElement).getContainingClassName();
              final String propertyName = ((DPPropertyElement) propertyElement).getPropertyName();
              final String propertyType = ((DPPropertyElement) propertyElement).getPropertyType();

              DynamicPropertyVirtual dynamicProperty = new DynamicPropertyVirtual(propertyName, containingClassName, module.getName(), propertyType);

              DynamicPropertiesManager.getInstance(myProject).removeDynamicProperty(dynamicProperty);
            }
          }
        },
        KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
        JComponent.WHEN_FOCUSED
    );

    // todo use "myTreeTable.setAutoCreateRowSorter(true);" since 1.6

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
            final String substringToHighlight = propertyElement.getHightlightedText();
            final String propertyName = propertyElement.getPropertyName();

            if (substringToHighlight != null) {
              final int begin = propertyName.indexOf(substringToHighlight);
              final String first = propertyName.substring(0, begin);
              append(first, SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
              final TextAttributes textAttributes = TextAttributes.ERASE_MARKER;
//              textAttributes.setEffectColor(new Color(200, 200, 200));
              textAttributes.setBackgroundColor(UIUtil.getListSelectionBackground());
              append(substringToHighlight, SimpleTextAttributes.fromTextAttributes(textAttributes));
              append(propertyName.substring(first.length() + substringToHighlight.length()), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
            } else {
              append(propertyName, SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
            }

          } else if (value instanceof DPPropertyTypeElement) {
            append(((DPPropertyTypeElement) value).getPropertyType(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
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

  class PropertyTypeColumnInfo extends ColumnInfo<DefaultMutableTreeNode, DPPropertyTypeElement> {
    public PropertyTypeColumnInfo(String name) {
      super(name);
    }

    public boolean isCellEditable(DefaultMutableTreeNode defaultMutableTreeNode) {
      return true;
    }

//    public TableCellEditor getEditor(DefaultMutableTreeNode o) {
//      return super.getEditor(o);    //To change body of overridden methods use File | Settings | File Templates.
//    }

    public DPPropertyTypeElement valueOf(DefaultMutableTreeNode treeNode) {
      Object userObject = treeNode.getUserObject();

      if (userObject instanceof DPPropertyElement) return ((DPPropertyElement) userObject).getPropertyTypeElement();

      return null;
    }
  }

  class ClassColumnInfo extends ColumnInfo<DefaultMutableTreeNode, DPElement> {
    public ClassColumnInfo(String name) {
      super(name);
    }

    public boolean isCellEditable(DefaultMutableTreeNode defaultMutableTreeNode) {
      return false;
    }

    public Class getColumnClass() {
      return TreeTableModel.class;
    }


    public DPElement valueOf(DefaultMutableTreeNode treeNode) {
      Object userObject = treeNode.getUserObject();
      if (userObject instanceof DPElement) return ((DPElement) userObject);

      return null;
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

      TreeNode containingClassNode = null;
      try {
        containingClassNode = rootNode.getFirstChild();
      } catch (Exception e) {
        return;
      }

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

  private Module getModule() {
    //TODO
    final VirtualFile currentFile = FileEditorManagerEx.getInstanceEx(myProject).getCurrentFile();

    if (currentFile == null) {
      //TODO
      final Module[] modules = ModuleManager.getInstance(myProject).getModules();
      return modules[0];
    }

    return ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(currentFile);
  }

  private void storeState() {
    if (myTreeTable != null && myTreeTable.getTree() != null) {
      final Module module = getModule();

      if (module != null) {
        myState = getState();
        module.putUserData(DYNAMIC_TOOLWINDOW_STATE_KEY, myState);
      }
    }
  }

  private void restoreState() {
    if (myTreeTable != null && myTreeTable.getTree() != null) {
      final Module module = getModule();

      if (module != null) {
        myState = module.getUserData(DYNAMIC_TOOLWINDOW_STATE_KEY);
      }

      TreeUtil.restoreExpandedPaths(myTreeTable.getTree(), myState.getExpandedElements());
    }
  }

  public DynamicTreeViewState getState() {
    DynamicTreeViewState structureViewState = new DynamicTreeViewState();
    if (myTreeTable.getTree() != null) {
      structureViewState.setExpandedElements(getExpandedElements());
      structureViewState.setSelectedElements(getSelectedElements());
    }
    return structureViewState;
  }

  private List<TreePath> getExpandedElements() {
    final JTree tree = myTreeTable.getTree();
    if (tree == null) return Collections.EMPTY_LIST;
    return TreeUtil.collectExpandedPaths(tree);
  }

  private List<TreePath> getSelectedElements() {
    final JTree tree = myTreeTable.getTree();
    TreePath[] selectionPaths = tree.getSelectionPaths();
    selectionPaths = selectionPaths != null ? selectionPaths : new TreePath[0];

    return tree != null ? Arrays.asList(selectionPaths) : Collections.EMPTY_LIST;
  }

  public TreeTable getTreeTable() {
    return myTreeTable;
  }
}