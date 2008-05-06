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

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsParameterListImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.ui.table.TableView;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Collections;

public class MethodParameterPanel extends AbstractInjectionPanel<MethodParameterInjection> {
  private final ParamModel myParamModel;

  LanguagePanel myLanguagePanel;  // read by reflection

  private JPanel myRoot;
  private JPanel myClassPanel;
  private JCheckBox myHierarchy;

  private JPanel myMethodPanel;

  private ReferenceEditorWithBrowseButton myClassField;
  private ReferenceEditorWithBrowseButton myMethodField;

  private JTable myParamsTable;

  private PsiMethod myMethod;

  public MethodParameterPanel(MethodParameterInjection injection, final Project project) {
    super(injection, project);
    $$$setupUI$$$(); // see IDEA-9987

    myParamModel = (ParamModel)myParamsTable.getModel();
    myParamModel.setSortable(false);
    myParamModel.setItems(Collections.<Param>emptyList());

    final TreeUpdateListener updateListener = new TreeUpdateListener();
    myClassField = new ReferenceEditorWithBrowseButton(new BrowseClassListener(project), project, new Function<String, Document>() {
      public Document fun(String s) {
        final Document document = ReferenceEditorWithBrowseButton.createTypeDocument(s, PsiManager.getInstance(project));
        document.addDocumentListener(updateListener);
        return document;
      }
    }, "");
    myClassPanel.add(myClassField, BorderLayout.CENTER);

    myMethodField = new ReferenceEditorWithBrowseButton(new BrowseMethodListener(project), project, new Function<String, Document>() {
      public Document fun(String s) {
        final Document document = EditorFactory.getInstance().createDocument(s);
        document.setReadOnly(true);
        document.addDocumentListener(updateListener);
        return document;
      }
    }, "");
    myMethodPanel.add(myMethodField, BorderLayout.CENTER);

    init(injection.copy());
  }

  @Nullable
  private PsiType getClassType() {
    final PsiDocumentManager dm = PsiDocumentManager.getInstance(myProject);
    final PsiFile psiFile = dm.getPsiFile(myClassField.getEditorTextField().getDocument());
    try {
      assert psiFile != null;
      return ((PsiTypeCodeFragment)psiFile).getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e1) {
      return null;
    }
    catch (PsiTypeCodeFragment.NoTypeException e1) {
      return null;
    }
  }

  @Nullable
  private PsiClass findClass(PsiType type) {
    if (type instanceof PsiClassType) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
      return facade.findClass(type.getCanonicalText(), GlobalSearchScope.allScope(myProject));
    }
    return null;
  }

  private void setMethodName(String s) {
    myMethodField.setText(s);
    updateParameters();
  }

  private void setClassName(String name) {
    myClassField.setText(name);
    myMethod = null;
    setMethodName("");
  }

  public String getClassName() {
    final PsiType type = getClassType();
    if (type == null) {
      return myClassField.getText();
    }
    return type.getCanonicalText();
  }

  @Nullable
  private PsiMethod makeMethod(String signature) {
    try {
      if (signature.trim().length() > 0) {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
        final PsiElementFactory ef = facade.getElementFactory();
        return ef.createMethodFromText("void " + signature + "{}", null);
      }
    }
    catch (IncorrectOperationException e) {
      // signature is not in form NAME(TYPE NAME)
    }
    return null;
  }

  private void setSelection(boolean[] selectedIndices) {
    final java.util.List<Param> list = myParamModel.getItems();
    for (int i = 0; i < selectedIndices.length; i++) {
      if (list.size() > i) {
        final Param param = list.get(i);
        if (param.isEditable()) {
          param.selected = selectedIndices[i];
        }
      }
    }
  }

  private boolean[] getSelection() {
    final java.util.List<Param> list = myParamModel.getItems();
    final boolean[] indices = new boolean[list.size()];

    for (int i = 0; i < indices.length; i++) {
      final Param param = list.get(i);
      indices[i] = param.selected;
    }
    return indices;
  }

  protected void apply(MethodParameterInjection other) {
    other.setClassName(getClassName());
    other.setMethodSignature(buildSignature(myMethod));
    other.setSelection(getSelection());
    other.setApplyInHierarchy(myHierarchy.isSelected());
  }

  protected void resetImpl() {
    setClassName(myOrigInjection.getClassName());
    myMethod = makeMethod(myOrigInjection.getMethodSignature());
    if (myMethod != null) {
      setMethodName(myMethod.getName());
    }
    setSelection(myOrigInjection.getSelection());
    myHierarchy.setSelected(myOrigInjection.isApplyInHierarchy());
  }

  @Nullable
  private String buildSignature(@Nullable PsiMethod method) {
    if (method == null) {
      return null;
    }

    final PsiParameterList list = method.getParameterList();
    final PsiParameter[] parameters = list.getParameters();
    final String s;
    if (parameters.length > 0) {
      // if there are no sources, parameter names are unknown. This trick gives the "decompiled" names
      if (list instanceof ClsParameterListImpl && parameters[0].getName() == null) {
        s = method.getName() + list.getText();
      }
      else {
        s = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                       PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_FQ_CLASS_NAMES);
      }
    }
    else {
      s = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME, 0) + "()";
    }
    return s;
  }

  public JPanel getComponent() {
    return myRoot;
  }

  private void createUIComponents() {
    myLanguagePanel = new LanguagePanel(myProject, myOrigInjection);
    myParamsTable = new TableView<Param>(new ParamModel());
  }

  private void updateParameters() {
    if (myMethod != null) {
      final PsiParameterList list = myMethod.getParameterList();
      final java.util.List<Param> params = ContainerUtil.map(list.getParameters(), new Function<PsiParameter, Param>() {
        public Param fun(PsiParameter s) {
          return new Param(list.getParameterIndex(s), s);
        }
      });
      if (!myParamModel.getItems().equals(params)) {
        myParamModel.setItems(params);
      }
    }
    else {
      myParamModel.setItems(Collections.<Param>emptyList());
    }
  }

  static class Param {
    private final int myIndex;
    final String myName;
    final PsiType myType;

    public boolean selected;

    public Param(int index, PsiParameter p) {
      myIndex = index;
      myName = p.getName();
      myType = p.getType();
    }

    public boolean isEditable() {
      return isInjectable(myType);
    }

    public static boolean isInjectable(PsiType type) {
      return type.equalsToText("java.lang.String") || type.equalsToText("java.lang.String...") || type.equalsToText("java.lang.String[]");
    }

    @SuppressWarnings({"RedundantIfStatement"})
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final Param param = (Param)o;

      if (myIndex != param.myIndex) return false;
      if (myName != null ? !myName.equals(param.myName) : param.myName != null) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = myIndex;
      result = 31 * result + (myName != null ? myName.hashCode() : 0);
      return result;
    }
  }

  static class ParamModel extends ListTableModel<Param> {

    public ParamModel() {
      super(new ColumnInfo<Param, Boolean>(" ") { // "" for the first column's name isn't a good idea
        final BooleanTableCellRenderer myRenderer = new MyCellRenderer();

        public Boolean valueOf(Param o) {
          return o.selected;
        }

        public int getWidth(JTable table) {
          return myRenderer.getPreferredSize().width;
        }

        public TableCellEditor getEditor(Param o) {
          return new DefaultCellEditor(new JCheckBox());
        }

        public TableCellRenderer getRenderer(Param param) {
          return myRenderer;
        }

        public void setValue(Param param, Boolean value) {
          param.selected = value;
        }

        public Class<Boolean> getColumnClass() {
          return Boolean.class;
        }

        public boolean isCellEditable(Param param) {
          return param.isEditable();
        }
      }, new ColumnInfo<Param, String>("Type") {
        public String valueOf(Param o) {
          return o.myType.getCanonicalText();
        }
      }, new ColumnInfo<Param, String>("Name") {
        public String valueOf(Param o) {
          return o.myName;
        }
      });
    }

    @SuppressWarnings({"unchecked"})
    private static class MyCellRenderer extends BooleanTableCellRenderer {
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        final JComponent component = (JComponent)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        final Param p = ((ListTableModel<Param>)table.getModel()).getItems().get(row);
        setEnabled(p.isEditable());
        return component;
      }
    }
  }

  private class BrowseClassListener implements ActionListener {
    private final Project myProject;

    public BrowseClassListener(Project project) {
      myProject = project;
    }

    public void actionPerformed(ActionEvent e) {
      final TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);
      final TreeClassChooser chooser = factory.createAllProjectScopeChooser("Select Class");
      chooser.showDialog();
      final PsiClass psiClass = chooser.getSelectedClass();
      if (psiClass != null) {
        setClassName(psiClass.getQualifiedName());
        updateTree();
      }
    }
  }

  private class BrowseMethodListener implements ActionListener {
    private final Project myProject;

    public BrowseMethodListener(Project project) {
      myProject = project;
    }

    public void actionPerformed(ActionEvent e) {
      final PsiType type = getClassType();

      final PsiClass psiClass = findClass(type);
      if (psiClass != null) {
        final PsiMethod[] psiMethods = psiClass.getMethods();
        final java.util.List<PsiMethod> methods = ContainerUtil.findAll(psiMethods, new Condition<PsiMethod>() {
          public boolean value(PsiMethod method) {
            final PsiModifierList modifiers = method.getModifierList();
            if (modifiers.hasModifierProperty(PsiModifier.PRIVATE) || modifiers.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
              return false;
            }
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            return ContainerUtil.find(parameters, new Condition<PsiParameter>() {
              public boolean value(PsiParameter p) {
                return Param.isInjectable(p.getType());
              }
            }) != null;
          }
        });
        final PsiMethodMember[] members =
            ContainerUtil.map2Array(methods, PsiMethodMember.class, new Function<PsiMethod, PsiMethodMember>() {
              public PsiMethodMember fun(PsiMethod psiMethod) {
                return new PsiMethodMember(psiMethod);
              }
            });
        final MemberChooser<PsiMethodMember> chooser = new MemberChooser<PsiMethodMember>(members, false, false, myProject, false);
        if (myMethod != null) {
          final PsiMethod selection = ContainerUtil.find(methods, new Condition<PsiMethod>() {
            public boolean value(PsiMethod method) {
              if (!method.getName().equals(myMethod.getName())) {
                return false;
              }
              final MethodSignature sig1 = method.getSignature(PsiSubstitutor.EMPTY);
              final MethodSignature sig2 = myMethod.getSignature(PsiSubstitutor.EMPTY);
              return Arrays.equals(sig1.getParameterTypes(), sig2.getParameterTypes());
            }
          });
          if (selection != null) {
            chooser.selectElements(new ClassMember[]{new PsiMethodMember(selection)});
          }
        }
        chooser.setCopyJavadocVisible(false);
        chooser.setTitle("Select Method");
        chooser.show();

        final java.util.List<PsiMethodMember> selection = chooser.getSelectedElements();
        if (chooser.isOK() && selection != null && selection.size() > 0) {
          final PsiMethod method = selection.get(0).getElement();
          myMethod = makeMethod(buildSignature(method));
          setMethodName(method.getName());
          updateTree();
        }

        return;
      }

      Messages.showErrorDialog(myProject, "Please select a valid class first", "Method Selection");
    }
  }

  private void $$$setupUI$$$() {
  }
}
