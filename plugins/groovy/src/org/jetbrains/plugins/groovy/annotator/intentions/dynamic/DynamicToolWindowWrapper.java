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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.*;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2008
 */
public class DynamicToolWindowWrapper {
  private final Project myProject;
  private ToolWindow myToolWindow = null;

  public DynamicToolWindowWrapper(Project project) {
    myProject = project;
  }

  public static DynamicToolWindowWrapper getInstance(Project project) {
    return ServiceManager.getService(project, DynamicToolWindowWrapper.class);
  }

  public static final String DYNAMIC_TOOLWINDOW_ID = GroovyBundle.message("dynamic.tool.window.id");
  private JPanel myTreeTablePanel;
  private JPanel myBigPanel;
  private ListTreeTableModelOnColumns myTreeTableModel;

  private static final int CLASS_OR_ELEMENT_NAME_COLUMN = 0;
  private static final int TYPE_COLUMN = 1;

  private static final String[] myColumnNames = {"Dynamic element", "Type"};

  private MyTreeTable myTreeTable;

  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicToolWindowWrapper");

  public TreeTable getTreeTable() {
    getToolWindow();

    return myTreeTable;
  }

  public ToolWindow getToolWindow() {
    if (myToolWindow == null) {
      myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(DYNAMIC_TOOLWINDOW_ID, true, ToolWindowAnchor.RIGHT);
      myToolWindow.setIcon(GroovyIcons.DYNAMIC_PROPERTY_TOOL_WINDOW_ICON);
      myToolWindow.setTitle(GroovyBundle.message("dynamic.window"));
      myToolWindow.setToHideOnEmptyContent(true);

      final JPanel panel = buildBigPanel();
      final ContentManager contentManager = myToolWindow.getContentManager();
      final Content content = contentManager.getFactory().createContent(panel, "", false);
      content.setPreferredFocusableComponent(myTreeTable);
      contentManager.addContent(content);
    }

    return myToolWindow;
  }

  private JPanel buildBigPanel() {
    myBigPanel = new JPanel(new BorderLayout());
    myBigPanel.setBackground(UIUtil.getFieldForegroundColor());

    final DynamicFilterComponent filter = new DynamicFilterComponent(GroovyBundle.message("dynamic.toolwindow.property.fiter"), 10);
    filter.setBackground(UIUtil.getLabelBackground());

    myBigPanel.add(new JLabel(GroovyBundle.message("dynamic.toolwindow.search.elements")), BorderLayout.NORTH);
    myBigPanel.add(filter, BorderLayout.NORTH);

    myTreeTablePanel = new JPanel(new BorderLayout());
    rebuildTreePanel();

    myBigPanel.add(myTreeTablePanel);
    myBigPanel.setPreferredSize(new Dimension(200, myBigPanel.getHeight()));

    myBigPanel.revalidate();
    return myBigPanel;
  }

  public void rebuildTreePanel() {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    buildTree(rootNode);

    rebuildTreeView(rootNode, false);
  }

  private void rebuildTreeView(DefaultMutableTreeNode root, boolean expandAll) {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    myTreeTablePanel.removeAll();

    final JBScrollPane treeTable = createTable(root);

    if (expandAll) {
      TreeUtil.expandAll(myTreeTable.getTree());
    }

    myTreeTablePanel.add(treeTable);
    myTreeTablePanel.revalidate();
  }

  private DefaultMutableTreeNode buildTree(DefaultMutableTreeNode rootNode) {
    final Collection<DClassElement> containingClasses = DynamicManager.getInstance(myProject).getAllContainingClasses();

    DefaultMutableTreeNode containingClassNode;
    for (DClassElement containingClassElement : containingClasses) {
      containingClassNode = new DefaultMutableTreeNode(containingClassElement);

      final Collection<DPropertyElement> properties =
        DynamicManager.getInstance(myProject).findDynamicPropertiesOfClass(containingClassElement.getName());

//      if (properties.length == 0) continue;

      DefaultMutableTreeNode propertyTreeNode;
      for (DPropertyElement property : properties) {

        propertyTreeNode = new DefaultMutableTreeNode(property);
        containingClassNode.add(propertyTreeNode);
      }

      DefaultMutableTreeNode methodTreeNode;
      final Set<DMethodElement> methods = containingClassElement.getMethods();

      for (DMethodElement methodElement : methods) {
        final String[] psiTypes = QuickfixUtil.getArgumentsTypes(methodElement.getPairs());

        final DMethodElement method = DynamicManager.getInstance(myProject)
          .findConcreteDynamicMethod(containingClassElement.getName(), methodElement.getName(), psiTypes);

        methodTreeNode = new DefaultMutableTreeNode(method);
        containingClassNode.add(methodTreeNode);
      }

      rootNode.add(containingClassNode);
    }
    return rootNode;
  }

  private JBScrollPane createTable(final MutableTreeNode myTreeRoot) {
    ColumnInfo[] columnInfos =
      {new ClassColumnInfo(myColumnNames[CLASS_OR_ELEMENT_NAME_COLUMN]), new PropertyTypeColumnInfo(myColumnNames[TYPE_COLUMN])};

    myTreeTableModel = new ListTreeTableModelOnColumns(myTreeRoot, columnInfos);

    myTreeTable = new MyTreeTable(myTreeTableModel);

    final MyColoredTreeCellRenderer treeCellRenderer = new MyColoredTreeCellRenderer();

    myTreeTable.setDefaultRenderer(String.class, new TableCellRenderer() {
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        if (value instanceof String) {
          final GrTypeElement typeElement;
          try {
            typeElement = GroovyPsiElementFactory.getInstance(myProject).createTypeElement(((String)value));
            if (typeElement != null){
              String shortName = typeElement.getType().getPresentableText();
              return new JLabel(shortName);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.debug("Type cannot be created", e);
          }
          return new JLabel(QuickfixUtil.shortenType((String)value));
        }

        return new JLabel();
      }
    });

    myTreeTable.setTreeCellRenderer(treeCellRenderer);

    myTreeTable.setRootVisible(false);
    myTreeTable.setSelectionMode(DefaultTreeSelectionModel.CONTIGUOUS_TREE_SELECTION);

    final MyPropertyTypeCellEditor typeCellEditor = new MyPropertyTypeCellEditor();

    typeCellEditor.addCellEditorListener(new CellEditorListener() {
      public void editingStopped(ChangeEvent e) {
        final TreeTableTree tree = getTree();

        String newTypeValue = ((MyPropertyTypeCellEditor)e.getSource()).getCellEditorValue();

        if (newTypeValue == null || tree == null) {
          myTreeTable.editingStopped(e);
          return;
        }

        try {
          GrTypeElement typeElement = GroovyPsiElementFactory.getInstance(myProject).createTypeElement(newTypeValue);
          if (typeElement != null) {
            String canonical = typeElement.getType().getCanonicalText();
            if (canonical != null) newTypeValue = canonical;
          }
        }
        catch (IncorrectOperationException ex) {
          //do nothing in case bad string is entered
        }

        final TreePath editingTypePath = tree.getSelectionPath();
        if (editingTypePath == null) return;

        final TreePath editingClassPath = editingTypePath.getParentPath();

        Object oldTypeValue = myTreeTable.getValueAt(tree.getRowForPath(editingTypePath), TYPE_COLUMN);

        if (!(oldTypeValue instanceof String)) {
          myTreeTable.editingStopped(e);
          return;
        }

        final Object editingPropertyObject = myTreeTable.getValueAt(tree.getRowForPath(editingTypePath), CLASS_OR_ELEMENT_NAME_COLUMN);
        final Object editingClassObject = myTreeTable.getValueAt(tree.getRowForPath(editingClassPath), CLASS_OR_ELEMENT_NAME_COLUMN);

        if (!(editingPropertyObject instanceof DItemElement) || !(editingClassObject instanceof DClassElement)) {
          myTreeTable.editingStopped(e);
          return;
        }

        final DItemElement dynamicElement = (DItemElement)editingPropertyObject;
        final String name = dynamicElement.getName();
        final String className = ((DClassElement)editingClassObject).getName();

        if (dynamicElement instanceof DPropertyElement) {
          DynamicManager.getInstance(myProject).replaceDynamicPropertyType(className, name, (String)oldTypeValue, newTypeValue);

        } else if (dynamicElement instanceof DMethodElement) {
          final List<MyPair> myPairList = ((DMethodElement)dynamicElement).getPairs();
          DynamicManager.getInstance(myProject).replaceDynamicMethodType(className, name, myPairList, (String)oldTypeValue, newTypeValue);
        }
      }

      public void editingCanceled(ChangeEvent e) {
        System.out.println("editing canceled");
        myTreeTable.editingCanceled(e);
      }
    });

    RefactoringListenerManager.getInstance(myProject).addListenerProvider(new RefactoringElementListenerProvider() {
      @Nullable
      public RefactoringElementListener getListener(final PsiElement element) {
        if (element instanceof PsiClass) {
          final String qualifiedName = ((PsiClass)element).getQualifiedName();

          return new RefactoringElementListener() {
            public void elementMoved(PsiElement newElement) {
              renameElement(qualifiedName, newElement);
            }

            public void elementRenamed(PsiElement newElement) {
              renameElement(qualifiedName, newElement);
            }

            private void renameElement(String oldClassName, PsiElement newElement) {
              if (newElement instanceof PsiClass) {
                final String newClassName = ((PsiClass)newElement).getQualifiedName();

                final DRootElement rootElement = DynamicManager.getInstance(myProject).getRootElement();
                final DClassElement oldClassElement = rootElement.getClassElement(oldClassName);
                final TreeNode oldClassNode = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)myTreeRoot, oldClassElement);

                DynamicManager.getInstance(myProject).replaceClassName(oldClassElement, newClassName);
                myTreeTableModel.nodeChanged(oldClassNode);
              }
            }
          };
        }
        return null;
      }
    });

    myTreeTable.setDefaultEditor(String.class, typeCellEditor);

    myTreeTable.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        deleteRow();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_FOCUSED);

    myTreeTable.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        final int selectionRow = myTreeTable.getTree().getLeadSelectionRow();
        myTreeTable.editCellAt(selectionRow, CLASS_OR_ELEMENT_NAME_COLUMN, event);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), JComponent.WHEN_FOCUSED);

    myTreeTable.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        final int selectionRow = myTreeTable.getTree().getLeadSelectionRow();
        myTreeTable.editCellAt(selectionRow, TYPE_COLUMN, event);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_F2, KeyEvent.CTRL_MASK), JComponent.WHEN_FOCUSED);

    // todo use "myTreeTable.setAutoCreateRowSorter(true);" since 1.6

    myTreeTable.getTree().setShowsRootHandles(true);
    myTreeTable.getTableHeader().setReorderingAllowed(false);

    myTreeTable.setPreferredScrollableViewportSize(new Dimension(300, myTreeTable.getRowHeight() * 10));
    myTreeTable.getColumn(myColumnNames[CLASS_OR_ELEMENT_NAME_COLUMN]).setPreferredWidth(200);
    myTreeTable.getColumn(myColumnNames[TYPE_COLUMN]).setPreferredWidth(160);

    JBScrollPane scrollpane = ScrollPaneFactory.createScrollPane(myTreeTable);

    scrollpane.setPreferredSize(new Dimension(600, 400));
    return scrollpane;
  }

  private void deleteRow() {
    final int[] rows = myTreeTable.getSelectedRows();

    boolean isShowDialog = true;
    final int rowsCount = rows.length;
    int i = 0;

    final TreeTableTree tree = myTreeTable.getTree();

    for (TreePath selectionPath : tree.getSelectionPaths()) {
      if (rowsCount > 1) isShowDialog = false;
      if (i++ == 0) isShowDialog = true;

      //class
      final TreePath parent = selectionPath.getParentPath();

      if (parent.getParentPath() == null) {
        //selectionPath is class

        final Object classRow = selectionPath.getLastPathComponent();
        if (!(classRow instanceof DefaultMutableTreeNode)) return;

        if (!removeClass(((DefaultMutableTreeNode)classRow), isShowDialog, rowsCount)) return;

      } else {
        //selectionPath is dynamic item
        final Object classRow = parent.getLastPathComponent();
        final Object dynamicRow = selectionPath.getLastPathComponent();

        if (!(classRow instanceof DefaultMutableTreeNode)) return;
        if (!(dynamicRow instanceof DefaultMutableTreeNode)) return;

        final DefaultMutableTreeNode dynamicItemNode = (DefaultMutableTreeNode)dynamicRow;
        final DefaultMutableTreeNode classNode = (DefaultMutableTreeNode)classRow;

        final boolean removeClass = classNode.getChildCount() == 1;
        if (!removeDynamicElement(dynamicItemNode, isShowDialog, rowsCount)) return;
        if (removeClass) {
          removeNamedElement((DNamedElement)classNode.getUserObject());
        }
      }
    }
    DynamicManager.getInstance(myProject).fireChange();
  }

  private boolean removeClass(DefaultMutableTreeNode classNode, boolean isShowDialog, int rowsCount) {
    final TreeNode rootObject = classNode.getParent();
    return rootObject instanceof DefaultMutableTreeNode && removeDynamicElement(classNode, isShowDialog, rowsCount);

  }

  private boolean removeDynamicElement(DefaultMutableTreeNode child, boolean isShowDialog, int rowsCount) {
    Object namedElement = child.getUserObject();

    if (!(namedElement instanceof DNamedElement)) return false;

    if (isShowDialog) {
      int result;
      if (rowsCount > 1) {
        result = Messages.showOkCancelDialog(myBigPanel, GroovyBundle.message("are.you.sure.to.delete.elements", String.valueOf(rowsCount)),
                                             GroovyBundle.message("dynamic.element.deletion"), Messages.getQuestionIcon());

      } else {
        result = Messages.showOkCancelDialog(myBigPanel, GroovyBundle.message("are.you.sure.to.delete.dynamic.property",
                                                                              ((DNamedElement)namedElement).getName()),
                                             GroovyBundle.message("dynamic.property.deletion"), Messages.getQuestionIcon());
      }

      if (result != DialogWrapper.OK_EXIT_CODE) return false;
    }

    removeNamedElement(((DNamedElement)namedElement));

    /*final Object selectionNode = selectionPath.getLastPathComponent();
    if (!(selectionNode instanceof DefaultMutableTreeNode)) return false;

    DefaultMutableTreeNode toSelect = (parent.getChildAfter(child) != null || parent.getChildCount() == 1 ?
        ((DefaultMutableTreeNode) selectionNode).getNextNode() :
        ((DefaultMutableTreeNode) selectionNode).getPreviousNode());

//    DefaultMutableTreeNode toSelect = toSelect != null ? (DefaultMutableTreeNode) toSelect.getLastPathComponent() : null;

    removeFromParent(parent, child);
    if (toSelect != null) {
      setSelectedNode(toSelect, myProject);
    }*/

    return true;
  }

  private void removeNamedElement(DNamedElement namedElement) {
    if (namedElement instanceof DClassElement) {
      DynamicManager.getInstance(myProject).removeClassElement((DClassElement)namedElement);
    } else if (namedElement instanceof DItemElement) {
      DynamicManager.getInstance(myProject).removeItemElement((DItemElement)namedElement);
    }
  }

  public void setSelectedNode(DefaultMutableTreeNode node) {
    JTree tree = myTreeTable.getTree();
    TreePath path = new TreePath(node.getPath());
    tree.expandPath(path.getParentPath());
    int row = tree.getRowForPath(path);
    myTreeTable.getSelectionModel().setSelectionInterval(row, row);
    myTreeTable.scrollRectToVisible(myTreeTable.getCellRect(row, 0, true));
    IdeFocusManager.getInstance(myProject).requestFocus(myTreeTable, true);
  }


  public void removeFromParent(DefaultMutableTreeNode parent, DefaultMutableTreeNode child) {
    int idx = myTreeTableModel.getIndexOfChild(parent, child);
    child.removeFromParent();
    myTreeTableModel.nodesWereRemoved(parent, new int[]{idx}, new TreeNode[]{child});
  }

  static class PropertyTypeColumnInfo extends ColumnInfo<DefaultMutableTreeNode, String> {
    public PropertyTypeColumnInfo(String name) {
      super(name);
    }

    public boolean isCellEditable(DefaultMutableTreeNode node) {
      final Object value = node.getUserObject();

      return !(value instanceof DClassElement);
    }

    public String valueOf(DefaultMutableTreeNode treeNode) {
      Object userObject = treeNode.getUserObject();

      if (userObject instanceof DItemElement) return ((DItemElement)userObject).getType();

      return null;
    }
  }

  class ClassColumnInfo extends ColumnInfo<DefaultMutableTreeNode, DNamedElement> {
    public ClassColumnInfo(String name) {
      super(name);
    }

    public boolean isCellEditable(DefaultMutableTreeNode treeNode) {
      final Object userObject = treeNode.getUserObject();

      return userObject instanceof DPropertyElement;
    }

    public Class getColumnClass() {
      return TreeTableModel.class;
    }

    public DNamedElement valueOf(DefaultMutableTreeNode treeNode) {
      Object userObject = treeNode.getUserObject();
      if (userObject instanceof DClassElement) return ((DClassElement)userObject);
      if (userObject instanceof DPropertyElement) return ((DPropertyElement)userObject);
      if (userObject instanceof DMethodElement) return ((DMethodElement)userObject);

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
      List<DefaultMutableTreeNode> classes = new ArrayList<DefaultMutableTreeNode>();
      List<DefaultMutableTreeNode> dynamicNodes = new ArrayList<DefaultMutableTreeNode>();

      if (rootNode.isLeaf()) return;
      DefaultMutableTreeNode classNode = (DefaultMutableTreeNode)rootNode.getFirstChild();
      while (classNode != null) {

        if (classNode.isLeaf()) {
          classNode = (DefaultMutableTreeNode)rootNode.getChildAfter(classNode);
          continue;
        }

        DefaultMutableTreeNode dynamicNode = (DefaultMutableTreeNode)classNode.getFirstChild();
        while (dynamicNode != null) {

          final Object childObject = dynamicNode.getUserObject();
          if (!(childObject instanceof DItemElement)) break;

          filterText = getFilter();
          if (filterText == null || "".equals(filterText)) {
            ((DItemElement)childObject).setHightlightedText("");

            dynamicNodes.add(dynamicNode);
            dynamicNode = (DefaultMutableTreeNode)classNode.getChildAfter(dynamicNode);
            continue;
          }

          final String name = (((DItemElement)childObject)).getName();

          if (name.contains(filterText)) {
            ((DItemElement)childObject).setHightlightedText(filterText);
            dynamicNodes.add(dynamicNode);
          }

          dynamicNode = (DefaultMutableTreeNode)classNode.getChildAfter(dynamicNode);
        }

        if (!dynamicNodes.isEmpty()) {
          classes.add(classNode);
        }

        classNode.removeAllChildren();

        for (DefaultMutableTreeNode node : dynamicNodes) {
          classNode.add(node);
        }

        dynamicNodes.clear();

        classNode = (DefaultMutableTreeNode)rootNode.getChildAfter(classNode);
      }
      rootNode.removeAllChildren();

      for (DefaultMutableTreeNode aClass : classes) {
        rootNode.add(aClass);
      }

      classes.clear();

      rebuildTreeView(rootNode, true);
      myBigPanel.invalidate();
    }
  }

  public ListTreeTableModelOnColumns getTreeTableModel() {
    getToolWindow();

    return myTreeTableModel;
  }

  private static class MyColoredTreeCellRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      value = ((DefaultMutableTreeNode)value).getUserObject();

      setPaintFocusBorder(false);

      if (!(value instanceof DNamedElement)) return;

      if (value instanceof DClassElement) {
        final String containingClassName = ((DClassElement)value).getName();
        //        append(className, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        final String classShortName = QuickfixUtil.shortenType(containingClassName);
        append(classShortName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }

      if (value instanceof DItemElement) {
        final DItemElement itemElement = ((DItemElement)value);
        final String substringToHighlight = itemElement.getHightlightedText();
        final String name = itemElement.getName();

        if (substringToHighlight != null) {
          appendHighlightName(substringToHighlight, name);
        } else {
          appendName(name);
        }

        if (value instanceof DMethodElement) {
          appendMethodParameters(name, (DMethodElement)value);
        } else if (value instanceof DPropertyElement) {
          setToolTipText(name);
        }
      }
    }

    private void appendHighlightName(String substringToHighlight, String name) {
      final int begin = name.indexOf(substringToHighlight);
//          if (name.length() <= begin) return;
      final String first = name.substring(0, begin);
      append(first, SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
      final TextAttributes textAttributes = TextAttributes.ERASE_MARKER;
      textAttributes.setBackgroundColor(UIUtil.getListSelectionBackground());
      append(substringToHighlight, SimpleTextAttributes.fromTextAttributes(textAttributes));
      append(name.substring(first.length() + substringToHighlight.length()), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
    }

    private void appendName(String name) {
      append(name, SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
    }

    private void appendMethodParameters(final String name, DMethodElement value) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("(");

      final String[] types = mapToUnqualified(QuickfixUtil.getArgumentsNames(value.getPairs()));
      for (int i = 0; i < types.length; i++) {
        if (i != 0) buffer.append(", ");

        String type = types[i];
        buffer.append(type);
      }
      buffer.append(")");

      append(buffer.toString(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
      setToolTipText(name + buffer.toString());
    }

    private static String[] mapToUnqualified(final String[] argumentsNames) {
      return ContainerUtil.map2Array(argumentsNames, String.class, new Function<String, String>() {
        public String fun(final String s) {
          if (s == null) return null;
          int index = s.lastIndexOf(".");
          if (index > 0 && index < s.length() - 1) return s.substring(index + 1);
          return s;
        }
      });
    }
  }

  private class MyPropertyTypeCellEditor extends AbstractTableCellEditor {
    final EditorTextField field;

    public MyPropertyTypeCellEditor() {
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(new GroovyCodeFragment(myProject, ""));
      field = new EditorTextField(document, myProject, GroovyFileType.GROOVY_FILE_TYPE);
    }

    public String getCellEditorValue() {
      return field.getText();
    }

    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      if (value instanceof String) {
        field.setText(((String)value));
      }

      return field;
    }
  }

  @Nullable
  private TreeTableTree getTree() {
    return myTreeTable != null ? myTreeTable.getTree() : null;
  }

  public class MyTreeTable extends TreeTable implements DataProvider {
    public MyTreeTable(TreeTableModel treeTableModel) {
      super(treeTableModel);
    }

    @Nullable
    public Object getData(@NonNls String dataId) {
      if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
        return getSelectedElement();

      } else if (LangDataKeys.PSI_FILE.is(dataId)) {
        final PsiElement element = getSelectedElement();

        if (element == null) return null;
        return element.getContainingFile();
      }

      return null;
    }

    private PsiElement getSelectedElement() {
      final TreePath path = getTree().getSelectionPath();

      if (path == null) return null;
      final Object selectedObject = path.getLastPathComponent();
      if (!(selectedObject instanceof DefaultMutableTreeNode)) return null;

      final DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode)selectedObject;
      final Object userObject = selectedNode.getUserObject();
      if (!(userObject instanceof DNamedElement)) return null;

      if (userObject instanceof DClassElement) {
        final DClassElement classElement = (DClassElement)userObject;

        try {
          final GrTypeElement typeElement = GroovyPsiElementFactory.getInstance(myProject).createTypeElement(classElement.getName());
          PsiType type = typeElement.getType();

          if (type instanceof PsiPrimitiveType) {
            type = ((PsiPrimitiveType)type).getBoxedType(PsiManager.getInstance(myProject), GlobalSearchScope.allScope(myProject));
          }

          if (!(type instanceof PsiClassType)) return null;
          return ((PsiClassType)type).resolve();

        }
        catch (IncorrectOperationException e) {
          return null;
        }
      } else if (userObject instanceof DItemElement) {
        final DItemElement itemElement = (DItemElement)userObject;

        final TreeNode parentNode = selectedNode.getParent();
        if (!(parentNode instanceof DefaultMutableTreeNode)) return null;

        final Object classObject = ((DefaultMutableTreeNode)parentNode).getUserObject();
        if (!(classObject instanceof DClassElement)) return null;

        final String className = ((DClassElement)classObject).getName();

        return itemElement.getPsi(PsiManager.getInstance(myProject), className);
      }
      return null;
    }
  }
}
