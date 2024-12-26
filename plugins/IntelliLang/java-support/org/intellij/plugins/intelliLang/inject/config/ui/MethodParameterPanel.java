/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.config.ui;

import com.intellij.CommonBundle;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.ui.*;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.plugins.intelliLang.IntelliLangBundle;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.*;

public final class MethodParameterPanel extends AbstractInjectionPanel<MethodParameterInjection> {
  LanguagePanel myLanguagePanel;  // read by reflection
  AdvancedPanel myAdvancedPanel;

  private JPanel myRoot;
  private JPanel myClassPanel;

  private TreeTableView myParamsTable;

  private final ReferenceEditorWithBrowseButton myClassField;
  private DefaultMutableTreeNode myRootNode;

  private final Map<PsiMethod, MethodParameterInjection.MethodInfo> myData = new HashMap<>();

  public MethodParameterPanel(MethodParameterInjection injection, final Project project) {
    super(injection, project);
    $$$setupUI$$$();

    myClassField = new ReferenceEditorWithBrowseButton(new BrowseClassListener(project), project, s -> {
      final Document document = PsiUtilEx.createDocument(s, project);
      document.addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(final @NotNull DocumentEvent e) {
          updateParamTree();
          updateInjectionPanelTree();
        }
      });
      return document;
    }, "");
    myClassPanel.add(myClassField, BorderLayout.CENTER);
    myParamsTable.setTableHeader(null);
    myParamsTable.getTree().setShowsRootHandles(true);
    myParamsTable.getTree().setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {

        final Object o = ((DefaultMutableTreeNode)value).getUserObject();
        setIcon(o instanceof PsiMethod ? IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method)
                                       : o instanceof PsiParameter ? IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Parameter)
                                                                   : null);
        final String name;
        if (o instanceof PsiMethod) {
          name = PsiFormatUtil.formatMethod((PsiMethod)o, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                            PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE);
        }
        else if (o instanceof PsiParameter) {
          name = PsiFormatUtil.formatVariable((PsiParameter)o, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE, PsiSubstitutor.EMPTY);
        }
        else name = null;
        final boolean missing = o instanceof PsiElement && !((PsiElement)o).isPhysical();
        if (name != null) {
          append(name, missing? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }

    });
    init(injection.copy());
    TreeTableSpeedSearch.installOn(myParamsTable, o -> {
      final Object userObject = ((DefaultMutableTreeNode)o.getLastPathComponent()).getUserObject();
      return userObject instanceof PsiNamedElement? ((PsiNamedElement)userObject).getName() : null;
    });
    new AnAction(CommonBundle.message("action.text.toggle")) {
      @Override
      public void actionPerformed(final @NotNull AnActionEvent e) {
        performToggleAction();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), myParamsTable);
  }

  private void updateInjectionPanelTree() {
    updateTree();
  }

  private void performToggleAction() {
    final Collection<DefaultMutableTreeNode> selectedInjections = myParamsTable.getSelection();
    boolean enabledExists = false;
    boolean disabledExists = false;
    for (DefaultMutableTreeNode node : selectedInjections) {
      final Boolean nodeSelected = isNodeSelected(node);
      if (Boolean.TRUE == nodeSelected) enabledExists = true;
      else if (Boolean.FALSE == nodeSelected) disabledExists = true;
      if (enabledExists && disabledExists) break;
    }
    boolean allEnabled = !enabledExists && disabledExists;
    for (DefaultMutableTreeNode node : selectedInjections) {
      setNodeSelected(node, allEnabled);
    }
    myParamsTable.updateUI();
  }

  private @Nullable PsiType getClassType() {
    final Document document = myClassField.getEditorTextField().getDocument();
    final PsiDocumentManager dm = PsiDocumentManager.getInstance(myProject);
    dm.commitDocument(document);
    final PsiFile psiFile = dm.getPsiFile(document);
    if (psiFile == null) return null;
    try {
      return ((PsiTypeCodeFragment)psiFile).getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException | PsiTypeCodeFragment.NoTypeException e1) {
      return null;
    }
  }

  private void setPsiClass(String name) {
    myClassField.setText(name);
  }

  private void updateParamTree() {
    rebuildTreeModel();
    refreshTreeStructure();
  }

  private void rebuildTreeModel() {
    myData.clear();
    ApplicationManager.getApplication().runReadAction(() -> {
      final PsiType classType = getClassType();
      final PsiClass[] classes = classType instanceof PsiClassType? JavaPsiFacade.getInstance(myProject).
        findClasses(classType.getCanonicalText(), GlobalSearchScope.allScope(myProject)) : PsiClass.EMPTY_ARRAY;
      if (classes.length == 0) return;
      final Set<String> visitedSignatures = new HashSet<>();
      for (PsiClass psiClass : classes) {
        for (PsiMethod method : psiClass.getMethods()) {
          final PsiModifierList modifiers = method.getModifierList();
          if (modifiers.hasModifierProperty(PsiModifier.PRIVATE) || modifiers.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) continue;
          if (MethodParameterInjection.isInjectable(method.getReturnType(), method.getProject()) ||
              ContainerUtil.find(method.getParameterList().getParameters(),
                                 p -> MethodParameterInjection.isInjectable(p.getType(), p.getProject())) != null) {
            final MethodParameterInjection.MethodInfo info = MethodParameterInjection.createMethodInfo(method);
            if (!visitedSignatures.add(info.getMethodSignature())) continue;
            myData.put(method, info);
          }
        }
      }
    });
  }

  private void refreshTreeStructure() {
    myRootNode.removeAllChildren();
    final ArrayList<PsiMethod> methods = new ArrayList<>(myData.keySet());
    methods.sort(Comparator.comparing(PsiMethod::getName).thenComparingInt(o -> o.getParameterList().getParametersCount()));
    for (PsiMethod method : methods) {
      final PsiParameter[] params = method.getParameterList().getParameters();
      final DefaultMutableTreeNode methodNode = new DefaultMutableTreeNode(method, true);
      myRootNode.add(methodNode);
      for (final PsiParameter parameter : params) {
        methodNode.add(new DefaultMutableTreeNode(parameter, false));
      }
    }
    final ListTreeTableModelOnColumns tableModel = (ListTreeTableModelOnColumns)myParamsTable.getTableModel();
    tableModel.reload();
    TreeUtil.expandAll(myParamsTable.getTree());
    myParamsTable.revalidate();
  }

  private String getClassName() {
    final PsiType type = getClassType();
    if (type == null) {
      return myClassField.getText();
    }
    return type.getCanonicalText();
  }


  @Override
  protected void apply(final MethodParameterInjection other) {
    final boolean applyMethods = ReadAction.compute(() -> {
      other.setClassName(getClassName());
      return getClassType() != null;
    }).booleanValue();
    if (applyMethods) {
      other.setMethodInfos(ContainerUtil.findAll(myData.values(), methodInfo -> methodInfo.isEnabled()));
    }
  }

  @Override
  protected void resetImpl() {
    setPsiClass(myOrigInjection.getClassName());

    rebuildTreeModel();
    final Map<String, MethodParameterInjection.MethodInfo> map = new HashMap<>();
    for (PsiMethod method : myData.keySet()) {
      final MethodParameterInjection.MethodInfo methodInfo = myData.get(method);
      map.put(methodInfo.getMethodSignature(), methodInfo);
    }
    for (MethodParameterInjection.MethodInfo info : myOrigInjection.getMethodInfos()) {
      final MethodParameterInjection.MethodInfo curInfo = map.get(info.getMethodSignature());
      if (curInfo != null) {
        System.arraycopy(info.getParamFlags(), 0, curInfo.getParamFlags(), 0, Math.min(info.getParamFlags().length, curInfo.getParamFlags().length));
        curInfo.setReturnFlag(info.isReturnFlag());
      }
      else {
        final PsiMethod missingMethod = MethodParameterInjection.makeMethod(myProject, info.getMethodSignature());
        myData.put(missingMethod, info.copy());
      }
    }
    refreshTreeStructure();
    final Enumeration enumeration = myRootNode.children();
    while (enumeration.hasMoreElements()) {
      PsiMethod method = (PsiMethod)((DefaultMutableTreeNode)enumeration.nextElement()).getUserObject();
      assert myData.containsKey(method);
    }
  }

  @Override
  public JPanel getComponent() {
    return myRoot;
  }

  private void createUIComponents() {
    myLanguagePanel = new LanguagePanel(myProject, myOrigInjection);
    myRootNode = new DefaultMutableTreeNode(null, true);
    myParamsTable = new MyView(new ListTreeTableModelOnColumns(myRootNode, createColumnInfos()));
    myAdvancedPanel = new AdvancedPanel(myProject, myOrigInjection);
  }

  private @Nullable Boolean isNodeSelected(final DefaultMutableTreeNode o) {
    final Object userObject = o.getUserObject();
    if (userObject instanceof PsiMethod method) {
      return MethodParameterInjection.isInjectable(method.getReturnType(), method.getProject()) ? myData.get(method).isReturnFlag() : null;
    }
    else if (userObject instanceof PsiParameter parameter) {
      final PsiMethod method = getMethodByNode(o);
      final int index = method.getParameterList().getParameterIndex(parameter);
      return MethodParameterInjection.isInjectable(parameter.getType(), method.getProject()) ? myData.get(method).getParamFlags()[index] : null;
    }
    return null;
  }

  private void setNodeSelected(final DefaultMutableTreeNode o, final boolean value) {
    final Object userObject = o.getUserObject();
    if (userObject instanceof PsiMethod) {
      myData.get((PsiMethod)userObject).setReturnFlag(value);
    }
    else if (userObject instanceof PsiParameter) {
      final PsiMethod method = getMethodByNode(o);
      final int index = method.getParameterList().getParameterIndex((PsiParameter)userObject);
      myData.get(method).getParamFlags()[index] = value;
    }
  }

  private static PsiMethod getMethodByNode(final DefaultMutableTreeNode o) {
    final Object userObject = o.getUserObject();
    if (userObject instanceof PsiMethod) return (PsiMethod)userObject;
    return (PsiMethod)((DefaultMutableTreeNode)o.getParent()).getUserObject();
  }

  private ColumnInfo[] createColumnInfos() {
    return new ColumnInfo[]{
        new ColumnInfo<DefaultMutableTreeNode, Boolean>(" ") { // "" for the first column's name isn't a good idea
          final BooleanTableCellRenderer myRenderer = new BooleanTableCellRenderer();

          @Override
          public Boolean valueOf(DefaultMutableTreeNode o) {
            return isNodeSelected(o);
          }

          @Override
          public int getWidth(JTable table) {
            return myRenderer.getPreferredSize().width;
          }

          @Override
          public TableCellEditor getEditor(DefaultMutableTreeNode o) {
            return new DefaultCellEditor(new JCheckBox());
          }

          @Override
          public TableCellRenderer getRenderer(DefaultMutableTreeNode o) {
            myRenderer.setEnabled(isCellEditable(o));
            return myRenderer;
          }

          @Override
          public void setValue(DefaultMutableTreeNode o, Boolean value) {
            setNodeSelected(o, Boolean.TRUE.equals(value));
          }

          @Override
          public Class<Boolean> getColumnClass() {
            return Boolean.class;
          }

          @Override
          public boolean isCellEditable(DefaultMutableTreeNode o) {
            return valueOf(o) != null;
          }

        }, new TreeColumnInfo(" ")
    };
  }

  private class BrowseClassListener implements ActionListener {
    private final Project myProject;

    BrowseClassListener(Project project) {
      myProject = project;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);
      final TreeClassChooser chooser = factory.createAllProjectScopeChooser(IntelliLangBundle.message("dialog.title.select.class"));
      chooser.showDialog();
      final PsiClass psiClass = chooser.getSelected();
      if (psiClass != null) {
        setPsiClass(psiClass.getQualifiedName());
        updateParamTree();
        updateInjectionPanelTree();
      }
    }
  }

  private static class MyView extends TreeTableView implements UiDataProvider {
    MyView(ListTreeTableModelOnColumns treeTableModel) {
      super(treeTableModel);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      Object userObject = TreeUtil.getUserObject(ContainerUtil.getFirstItem(getSelection()));
      sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
        return userObject instanceof PsiElement o ? o : null;
      });
    }
  }

  private void $$$setupUI$$$() {
  }

}
