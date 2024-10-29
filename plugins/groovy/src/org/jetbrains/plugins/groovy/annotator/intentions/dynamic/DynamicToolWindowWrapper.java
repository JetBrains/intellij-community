// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.ColumnName;
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
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.*;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service(Service.Level.PROJECT)
public final class DynamicToolWindowWrapper {
  private static final Logger LOG = Logger.getInstance(DynamicToolWindowWrapper.class);

  private static final int CLASS_OR_ELEMENT_NAME_COLUMN = 0;
  private static final int TYPE_COLUMN = 1;

  private static final @ColumnName String[] myColumnNames = {
    GroovyBundle.message("dynamic.members.column.name.element"),
    GroovyBundle.message("dynamic.members.column.name.type")
  };

  private final Project myProject;
  private ToolWindow myToolWindow = null;

  private JPanel myTreeTablePanel;
  private SimpleToolWindowPanel myBigPanel;
  private ListTreeTableModelOnColumns myTreeTableModel;
  private MyTreeTable myTreeTable;

  public DynamicToolWindowWrapper(Project project) {
    myProject = project;
  }

  public static DynamicToolWindowWrapper getInstance(Project project) {
    return project.getService(DynamicToolWindowWrapper.class);
  }

  public TreeTable getTreeTable() {
    getToolWindow();

    return myTreeTable;
  }

  public ToolWindow getToolWindow() {
    if (myToolWindow == null) {
      myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(GroovyBundle.message("dynamic.tool.window.id"), false, ToolWindowAnchor.RIGHT);
      myToolWindow.setIcon(JetgroovyIcons.Groovy.Groovy_13x13);

      final JPanel panel = buildBigPanel();
      final ContentManager contentManager = myToolWindow.getContentManager();
      final Content content = contentManager.getFactory().createContent(panel, "", false);
      content.setPreferredFocusableComponent(myTreeTable);
      contentManager.addContent(content);
    }

    return myToolWindow;
  }

  private JPanel buildBigPanel() {
    myBigPanel = new SimpleToolWindowPanel(true);

    myBigPanel.setBackground(UIUtil.getFieldForegroundColor());

    final JPanel panel = new JPanel(new BorderLayout());
    myBigPanel.add(panel, BorderLayout.CENTER);

    myTreeTablePanel = new JPanel(new BorderLayout());
    rebuildTreePanel();

    panel.add(myTreeTablePanel);
    myBigPanel.setPreferredSize(new Dimension(200, myBigPanel.getHeight()));
    myBigPanel.setToolbar(createToolbar().getComponent());

    myBigPanel.revalidate();
    return myBigPanel;
  }

  private static ActionToolbar createToolbar() {
    return ActionManager.getInstance().createActionToolbar("Groovy.Dynamic.Toolbar", createActions(), true);
  }

  private static ActionGroup createActions() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RemoveDynamicAction());
    group.add(Separator.getInstance());
    group.add(new ExpandAllAction());
    group.add(new CollapseAllAction());
    return group;
  }

  public void rebuildTreePanel() {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    buildTree(rootNode);

    rebuildTreeView(rootNode, false);
  }

  private void rebuildTreeView(DefaultMutableTreeNode root, boolean expandAll) {
    myTreeTablePanel.removeAll();

    final JScrollPane treeTable = createTable(root);

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

  private JScrollPane createTable(final MutableTreeNode myTreeRoot) {
    ColumnInfo[] columnInfos = {new ClassColumnInfo(), new PropertyTypeColumnInfo()};

    myTreeTableModel = new ListTreeTableModelOnColumns(myTreeRoot, columnInfos);

    myTreeTable = new MyTreeTable(myTreeTableModel);

    TreeTableSpeedSearch.installOn(myTreeTable, o -> {
      final Object node = o.getLastPathComponent();
      if (node instanceof DefaultMutableTreeNode) {
        final Object object = ((DefaultMutableTreeNode)node).getUserObject();
        if (object instanceof DNamedElement) {
          return ((DNamedElement)object).getName();
        }
      }
      return "";
    });

    PopupHandler.installPopupMenu(myTreeTable, new DefaultActionGroup(new RemoveDynamicAction()), "DynamicToolWindowWrapperPopup");

    final MyColoredTreeCellRenderer treeCellRenderer = new MyColoredTreeCellRenderer();

    myTreeTable.setDefaultRenderer(String.class, new TableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        if (value instanceof String) {
          try {
            final PsiType type = JavaPsiFacade.getElementFactory(myProject).createTypeFromText((String)value, null);
            String shortName = type.getPresentableText();
            return new JLabel(shortName);
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
    myTreeTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    final MyPropertyTypeCellEditor typeCellEditor = new MyPropertyTypeCellEditor();

    typeCellEditor.addCellEditorListener(new CellEditorListener() {
      @Override
      public void editingStopped(ChangeEvent e) {
        final TreeTableTree tree = getTree();

        String newTypeValue = ((MyPropertyTypeCellEditor)e.getSource()).getCellEditorValue();

        if (tree == null) {
          myTreeTable.editingStopped(e);
          return;
        }

        try {
          final PsiType type = JavaPsiFacade.getElementFactory(myProject).createTypeFromText(newTypeValue, null);
          newTypeValue = type.getCanonicalText();
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

        if (!(editingPropertyObject instanceof DItemElement dynamicElement) || !(editingClassObject instanceof DClassElement)) {
          myTreeTable.editingStopped(e);
          return;
        }

        final String name = dynamicElement.getName();
        final String className = ((DClassElement)editingClassObject).getName();

        if (dynamicElement instanceof DPropertyElement) {
          DynamicManager.getInstance(myProject).replaceDynamicPropertyType(className, name, (String)oldTypeValue, newTypeValue);
        }
        else if (dynamicElement instanceof DMethodElement) {
          final List<ParamInfo> myPairList = ((DMethodElement)dynamicElement).getPairs();
          DynamicManager.getInstance(myProject).replaceDynamicMethodType(className, name, myPairList, (String)oldTypeValue, newTypeValue);
        }
      }

      @Override
      public void editingCanceled(ChangeEvent e) {
        myTreeTable.editingCanceled(e);
      }
    });

    RefactoringListenerManager.getInstance(myProject).addListenerProvider(new RefactoringElementListenerProvider() {
      @Override
      @Nullable
      public RefactoringElementListener getListener(final PsiElement element) {
        if (element instanceof PsiClass) {
          final String qualifiedName = ((PsiClass)element).getQualifiedName();

          return new RefactoringElementListener() {
            @Override
            public void elementMoved(@NotNull PsiElement newElement) {
              renameElement(qualifiedName, newElement);
            }

            @Override
            public void elementRenamed(@NotNull PsiElement newElement) {
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
      @Override
      public void actionPerformed(ActionEvent event) {
        final int selectionRow = myTreeTable.getTree().getLeadSelectionRow();
        myTreeTable.editCellAt(selectionRow, CLASS_OR_ELEMENT_NAME_COLUMN, event);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), JComponent.WHEN_FOCUSED);

    myTreeTable.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        final int selectionRow = myTreeTable.getTree().getLeadSelectionRow();
        myTreeTable.editCellAt(selectionRow, TYPE_COLUMN, event);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_F2, InputEvent.CTRL_MASK), JComponent.WHEN_FOCUSED);

    myTreeTable.getTree().setShowsRootHandles(true);
    myTreeTable.getTableHeader().setReorderingAllowed(false);

    myTreeTable.setVisibleRowCount(10);
    myTreeTable.getColumn(myColumnNames[CLASS_OR_ELEMENT_NAME_COLUMN]).setPreferredWidth(200);
    myTreeTable.getColumn(myColumnNames[TYPE_COLUMN]).setPreferredWidth(160);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTreeTable);

    scrollPane.setPreferredSize(JBUI.size(600, 400));
    return scrollPane;
  }

  void deleteRow() {

    boolean isShowDialog = true;
    final int rowsCount = myTreeTable.getSelectedRows().length;
    int i = 0;

    final TreePath[] paths = myTreeTable.getTree().getSelectionPaths();
    if (paths == null) return;

    for (TreePath selectionPath : paths) {
      if (rowsCount > 1) isShowDialog = false;
      if (i == 0) isShowDialog = true;
      i++;

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

        if (!(classRow instanceof DefaultMutableTreeNode classNode)) return;
        if (!(dynamicRow instanceof DefaultMutableTreeNode dynamicItemNode)) return;

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

      if (result != Messages.OK) return false;
    }

    removeNamedElement(((DNamedElement)namedElement));

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

  @Nullable
  private TreeTableTree getTree() {
    return myTreeTable != null ? myTreeTable.getTree() : null;
  }

  private static class PropertyTypeColumnInfo extends ColumnInfo<DefaultMutableTreeNode, String> {

    PropertyTypeColumnInfo() {
      super(GroovyBundle.message("dynamic.members.column.name.type"));
    }

    @Override
    public boolean isCellEditable(DefaultMutableTreeNode node) {
      final Object value = node.getUserObject();

      return !(value instanceof DClassElement);
    }

    @Override
    public String valueOf(DefaultMutableTreeNode treeNode) {
      Object userObject = treeNode.getUserObject();

      if (userObject instanceof DItemElement) return ((DItemElement)userObject).getType();

      return null;
    }
  }

  private static class ClassColumnInfo extends ColumnInfo<DefaultMutableTreeNode, DNamedElement> {

    ClassColumnInfo() {
      super(GroovyBundle.message("dynamic.members.column.name.element"));
    }

    @Override
    public boolean isCellEditable(DefaultMutableTreeNode treeNode) {
      final Object userObject = treeNode.getUserObject();

      return userObject instanceof DPropertyElement;
    }

    @Override
    public Class getColumnClass() {
      return TreeTableModel.class;
    }

    @Override
    public DNamedElement valueOf(DefaultMutableTreeNode treeNode) {
      Object userObject = treeNode.getUserObject();
      if (userObject instanceof DClassElement) return ((DClassElement)userObject);
      if (userObject instanceof DPropertyElement) return ((DPropertyElement)userObject);
      if (userObject instanceof DMethodElement) return ((DMethodElement)userObject);

      return null;
    }
  }

  public ListTreeTableModelOnColumns getTreeTableModel() {
    getToolWindow();

    return myTreeTableModel;
  }

  private static class MyColoredTreeCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
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

      if (value instanceof DItemElement itemElement) {
        final String name = itemElement.getName();

        appendName(name);

        if (value instanceof DMethodElement) {
          appendMethodParameters((DMethodElement)value);
        }
      }
    }

    private void appendName(@NlsContexts.Label String name) {
      append(name, SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
    }

    private void appendMethodParameters(DMethodElement value) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("(");

      final String[] types = mapToUnqualified(QuickfixUtil.getArgumentsNames(value.getPairs()));
      for (int i = 0; i < types.length; i++) {
        if (i != 0) buffer.append(", ");

        String type = types[i];
        buffer.append(type);
      }
      buffer.append(")");

      //noinspection HardCodedStringLiteral
      append(buffer.toString(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
    }

    private static String[] mapToUnqualified(final String[] argumentsNames) {
      return ContainerUtil.map2Array(argumentsNames, String.class, (NullableFunction<String, String>)s -> {
        if (s == null) return null;
        int index = s.lastIndexOf(".");
        if (index > 0 && index < s.length() - 1) return s.substring(index + 1);
        return s;
      });
    }
  }

  private class MyPropertyTypeCellEditor extends AbstractTableCellEditor {
    final EditorTextField field;

    MyPropertyTypeCellEditor() {
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(new GroovyCodeFragment(myProject, ""));
      field = new EditorTextField(document, myProject, GroovyFileType.GROOVY_FILE_TYPE);
    }

    @Override
    public String getCellEditorValue() {
      return field.getText();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      if (value instanceof String) {
        field.setText(((String)value));
      }

      return field;
    }
  }

  private class MyTreeTable extends TreeTable implements UiDataProvider {
    MyTreeTable(TreeTableModel treeTableModel) {
      super(treeTableModel);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, new DeleteProvider() {
        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }

        @Override
        public void deleteElement(@NotNull DataContext dataContext) {
          deleteRow();
        }

        @Override
        public boolean canDeleteElement(@NotNull DataContext dataContext) {
          return myTreeTable.getTree().getSelectionPaths() != null;
        }
      });
      TreePath path = getTree().getSelectionPath();
      if (path == null) return;
      sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
        return getElementFromPath(path);
      });
      sink.lazy(CommonDataKeys.PSI_FILE, () -> {
        PsiElement element = getElementFromPath(path);
        return element == null ? null : element.getContainingFile();
      });
    }

    private @Nullable PsiElement getElementFromPath(@NotNull TreePath path) {
      final Object selectedObject = path.getLastPathComponent();
      if (!(selectedObject instanceof DefaultMutableTreeNode selectedNode)) return null;

      final Object userObject = selectedNode.getUserObject();
      if (!(userObject instanceof DNamedElement)) return null;

      if (userObject instanceof DClassElement classElement) {

        try {
          PsiType type  = JavaPsiFacade.getElementFactory(myProject).createTypeFromText(classElement.getName(), null);

          if (type instanceof PsiPrimitiveType) {
            type = ((PsiPrimitiveType)type).getBoxedType(PsiManager.getInstance(myProject), GlobalSearchScope.allScope(myProject));
          }

          if (!(type instanceof PsiClassType)) return null;
          return ((PsiClassType)type).resolve();

        }
        catch (IncorrectOperationException e) {
          return null;
        }
      } else if (userObject instanceof DItemElement itemElement) {

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
