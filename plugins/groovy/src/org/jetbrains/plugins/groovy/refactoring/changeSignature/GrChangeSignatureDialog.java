/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import com.intellij.refactoring.changeSignature.ExceptionsTableModel;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.ui.CodeFragmentTableCellRenderer;
import com.intellij.refactoring.ui.JavaCodeFragmentTableCellEditor;
import com.intellij.refactoring.ui.MethodSignatureComponent;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Function;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.refactoring.ui.GrCodeFragmentTableCellEditor;
import org.jetbrains.plugins.groovy.refactoring.ui.GrMethodSignatureComponent;
import org.jetbrains.plugins.groovy.refactoring.ui.GroovyComboboxVisibilityPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.List;

import static org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle.message;

/**
 * @author Maxim.Medvedev
 */
public class GrChangeSignatureDialog extends RefactoringDialog {
  private EditorTextField myNameField;
  private EditorTextField myReturnTypeField;
  private JBTable myParameterTable;
  private JPanel contentPane;
  private JLabel myNameLabel;
  private JLabel myReturnTypeLabel;
  private JRadioButton myDelegateRadioButton;
  private JPanel myDelegatePanel;
  private GroovyComboboxVisibilityPanel myVisibilityPanel;
  private MethodSignatureComponent mySignaturePreview;
  private JComponent myTabPanel;
  private GrParameterTableModel myParameterModel;
  private GrMethod myMethod;
  private PsiTypeCodeFragment myReturnTypeCodeFragment;
  private ExceptionsTableModel myExceptionTableModel;
  private static final String INDENT = "    ";

  public GrChangeSignatureDialog(@NotNull Project project, GrMethod method) {
    super(project, true);
    myMethod = method;

    init();
    updateSignature();
  }

  protected void init() {
    super.init();

    setTitle(ChangeSignatureHandler.REFACTORING_NAME);

    final PsiClass psiClass = myMethod.getContainingClass();
    if (psiClass == null) return;
    if (psiClass.isInterface()) {
      myDelegatePanel.setVisible(false);
    }

    myVisibilityPanel.setVisibility(VisibilityUtil.getVisibilityModifier(myMethod.getModifierList()));

    myVisibilityPanel.addListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateSignature();
      }
    });
  }

  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }


  private void createUIComponents() {
    createNameAndReturnTypeEditors();
    createSignaturePreview();

    JPanel paramPanel = createParametersPanel();
    paramPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
    JPanel exceptionPanel = createExceptionsPanel();
    exceptionPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

    final TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper(getDisposable());
    tabbedPane.addTab("Parameters", paramPanel);
    tabbedPane.addTab("Exceptions", exceptionPanel);

    myTabPanel = tabbedPane.getComponent();

    for (JComponent c : UIUtil.findComponentsOfType(myTabPanel, JComponent.class)) {
      c.setFocusCycleRoot(false);
      c.setFocusTraversalPolicy(null);
    }
  }

  private void createSignaturePreview() {
    mySignaturePreview = new GrMethodSignatureComponent("", myProject);
  }

  private void createNameAndReturnTypeEditors() {
    myNameField = new EditorTextField("", myProject,GroovyFileType.GROOVY_FILE_TYPE);

    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(myProject);
    myReturnTypeCodeFragment = factory.createTypeCodeFragment("", myMethod, true, JavaCodeFragmentFactory.ALLOW_VOID);
    final Document document = PsiDocumentManager.getInstance(myProject).getDocument(myReturnTypeCodeFragment);
    myReturnTypeField = new EditorTextField(document, myProject, myReturnTypeCodeFragment.getFileType());

    myNameField.setText(myMethod.getName());
    final GrTypeElement element = myMethod.getReturnTypeElementGroovy();
    if (element != null) {
      myReturnTypeField.setText(element.getText());
    }

    myReturnTypeLabel = new JLabel();
    myReturnTypeLabel.setLabelFor(myReturnTypeField);

    myNameLabel = new JLabel();
    myNameLabel.setLabelFor(myNameField);

    final DocumentListener listener = new DocumentListener() {
      @Override
      public void beforeDocumentChange(DocumentEvent event) {
      }

      @Override
      public void documentChanged(DocumentEvent event) {
        updateSignature();
      }
    };
    myReturnTypeField.addDocumentListener(listener);
    myNameField.addDocumentListener(listener);
  }

  private JPanel createParametersPanel() {
    myParameterModel = new GrParameterTableModel(myMethod, myProject);
    myParameterModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        updateSignature();
      }
    });
    myParameterTable = new JBTable(myParameterModel);
    myParameterTable.setPreferredScrollableViewportSize(new Dimension(550, myParameterTable.getRowHeight() * 8));

    myParameterTable.setCellSelectionEnabled(true);
    final TableColumnModel columnModel = myParameterTable.getColumnModel();

    columnModel.getColumn(0).setCellRenderer(new CodeFragmentTableCellRenderer(myProject));
    columnModel.getColumn(1).setCellRenderer(new CodeFragmentTableCellRenderer(myProject, GroovyFileType.GROOVY_FILE_TYPE));
    columnModel.getColumn(2).setCellRenderer(new CodeFragmentTableCellRenderer(myProject, GroovyFileType.GROOVY_FILE_TYPE));
    columnModel.getColumn(3).setCellRenderer(new CodeFragmentTableCellRenderer(myProject, GroovyFileType.GROOVY_FILE_TYPE));
    columnModel.getColumn(4).setCellRenderer(new BooleanTableCellRenderer());

    columnModel.getColumn(0).setCellEditor(new JavaCodeFragmentTableCellEditor(myProject));
    columnModel.getColumn(1).setCellEditor(new GrCodeFragmentTableCellEditor(myProject));
    columnModel.getColumn(2).setCellEditor(new GrCodeFragmentTableCellEditor(myProject));
    columnModel.getColumn(3).setCellEditor(new GrCodeFragmentTableCellEditor(myProject));
    columnModel.getColumn(4).setCellEditor(new BooleanTableCellEditor(false));

    if (myParameterModel.getRowCount() > 0) {
      myParameterTable.setRowSelectionInterval(0, 0);
      myParameterTable.setColumnSelectionInterval(0, 0);
    }

    return ToolbarDecorator.createDecorator(myParameterTable).createPanel();
  }

  private JPanel createExceptionsPanel() {
    myExceptionTableModel = new ExceptionsTableModel(myMethod);
    myExceptionTableModel.setTypeInfos(myMethod);
    myExceptionTableModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        updateSignature();
      }
    });
    JBTable exceptionTable = new JBTable(myExceptionTableModel);
    exceptionTable.setPreferredScrollableViewportSize(new Dimension(200, exceptionTable.getRowHeight() * 8));

    exceptionTable.getColumnModel().getColumn(0).setCellRenderer(new CodeFragmentTableCellRenderer(myProject));
    exceptionTable.getColumnModel().getColumn(0).setCellEditor(new JavaCodeFragmentTableCellEditor(myProject));

    if (myExceptionTableModel.getRowCount() > 0) {
      exceptionTable.setRowSelectionInterval(0, 0);
      exceptionTable.setColumnSelectionInterval(0, 0);
    }

    myExceptionTableModel.addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {
        updateSignature();
      }
    });

    return ToolbarDecorator.createDecorator(exceptionTable).createPanel();
  }


  private void updateSignature() {
    mySignaturePreview.setSignature(generateSignatureText());
  }

  private String generateSignatureText() {
    String name = getNewName();
    String type = myReturnTypeField.getText().trim();

    StringBuilder builder = new StringBuilder();
    builder.append(myVisibilityPanel.getVisibility()).append(' ');
    if (!type.isEmpty()) {
      builder.append(type).append(' ');
    }
    builder.append(name).append('(');

    final List<GrTableParameterInfo> infos = myParameterModel.getParameterInfos();
    if (infos.size() > 0) {
      final List<String> paramsText = ContainerUtil.map(infos, new Function<GrTableParameterInfo, String>() {
        public String fun(GrTableParameterInfo grParameterInfo) {
          return generateParameterText(grParameterInfo);
        }
      });
      builder.append("\n").append(INDENT);
      builder.append(StringUtil.join(paramsText, ",\n" + INDENT));
      builder.append('\n');
    }
    builder.append(')');

    final PsiTypeCodeFragment[] exceptions = myExceptionTableModel.getTypeCodeFragments();
    if (exceptions.length > 0) {
      builder.append("\nthrows\n");
      final List<String> exceptionNames = ContainerUtil.map(exceptions, new Function<PsiTypeCodeFragment, String>() {
        public String fun(PsiTypeCodeFragment fragment) {
          return fragment.getText();
        }
      });

      builder.append(INDENT).append(StringUtil.join(exceptionNames, ",\n" + INDENT));
    }
    return builder.toString();
  }


  private static String generateParameterText(GrTableParameterInfo info) {
    StringBuilder builder = new StringBuilder();
    final PsiTypeCodeFragment typeFragment = info.getTypeFragment();
    String typeText = typeFragment != null ? typeFragment.getText().trim() : GrModifier.DEF;
    if (typeText.length() == 0) typeText = GrModifier.DEF;
    builder.append(typeText).append(' ');
    final GroovyCodeFragment nameFragment = info.getNameFragment();
    builder.append(nameFragment != null ? nameFragment.getText().trim() : "");
    final GroovyCodeFragment defaultInitializer = info.getDefaultInitializerFragment();

    final String defaultInitializerText = defaultInitializer != null ? defaultInitializer.getText().trim() : "";
    if (defaultInitializerText.length() > 0) {
      builder.append(" = ").append(defaultInitializerText);
    }
    return builder.toString();
  }

  @Override
  protected void doAction() {
    if (!validateInputData()) {
      return;
    }

    TableUtil.stopEditing(myParameterTable);
    String modifier = myVisibilityPanel.getVisibility();

    PsiType returnType = null;
    try {
      returnType = myReturnTypeCodeFragment.getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException ignored) {
    }
    catch (PsiTypeCodeFragment.NoTypeException ignored) {
    }

    String newName = getNewName();
    final List<GrTableParameterInfo> tableParameterInfos = myParameterModel.getParameterInfos();
    final List<GrParameterInfo> parameterInfos = ContainerUtil.map(tableParameterInfos, new Function<GrTableParameterInfo, GrParameterInfo>() {
      public GrParameterInfo fun(GrTableParameterInfo info) {
        return info.generateParameterInfo();
      }
    });
    final ThrownExceptionInfo[] exceptionInfos = myExceptionTableModel.getThrownExceptions();
    final CanonicalTypes.Type type = returnType == null ? null : CanonicalTypes.createTypeWrapper(returnType);
    final GrChangeInfoImpl info =
      new GrChangeInfoImpl(myMethod, modifier, type, newName, parameterInfos, exceptionInfos, myDelegateRadioButton.isSelected());
    invokeRefactoring(new GrChangeSignatureProcessor(myProject, info));
  }

  private String getNewName() {
    return myNameField.getText().trim();
  }

  private void showErrorHint(String hint) {
    CommonRefactoringUtil.showErrorHint(myProject, null, hint, message("incorrect.data"), HelpID.CHANGE_SIGNATURE);
  }

  private boolean isGroovyMethodName(String name) {
    String methodText = "def " + name + "(){}";
    try {
      final GrMethod method = GroovyPsiElementFactory.getInstance(getProject()).createMethodFromText(methodText);
      return method != null;
    }
    catch (Throwable e) {
      return false;
    }
  }

  private boolean validateInputData() {
    if (!isGroovyMethodName(getNewName())) {
      showErrorHint(message("name.is.wrong", getNewName()));
      return false;
    }

    if (!checkType(myReturnTypeCodeFragment, true)) {
      showErrorHint(message("return.type.is.wrong"));
      return false;
    }

    List<GrTableParameterInfo> parameterInfos = myParameterModel.getParameterInfos();
    for (int i = 0; i < parameterInfos.size(); i++) {
      GrTableParameterInfo info = parameterInfos.get(i);
      if (!StringUtil.isJavaIdentifier(info.getName())) {
        showErrorHint(message("name.is.wrong", info.getName()));
        return false;
      }
      if (!checkType(info.getTypeFragment(), i == parameterInfos.size() - 1)) {
        showErrorHint(message("type.for.parameter.is.incorrect", info.getName()));
        return false;
      }
      String defaultValue = info.getDefaultValue();
      final String initializer = info.getDefaultInitializerFragment().getText();
      if (info.getOldIndex() < 0 && defaultValue.trim().length() == 0 && initializer.trim().length() == 0) {
        showErrorHint(message("specify.default.value", info.getName()));
        return false;
      }
    }

    ThrownExceptionInfo[] exceptionInfos = myExceptionTableModel.getThrownExceptions();
    PsiTypeCodeFragment[] typeCodeFragments = myExceptionTableModel.getTypeCodeFragments();
    for (int i = 0; i < exceptionInfos.length; i++) {
      ThrownExceptionInfo exceptionInfo = exceptionInfos[i];
      PsiTypeCodeFragment typeCodeFragment = typeCodeFragments[i];
      try {
        PsiType type = typeCodeFragment.getType();
        if (!(type instanceof PsiClassType)) {
          showErrorHint(message("changeSignature.wrong.type.for.exception", typeCodeFragment.getText()));
          return false;
        }

        PsiClassType throwable = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory()
          .createTypeByFQClassName("java.lang.Throwable", myMethod.getResolveScope());
        if (!throwable.isAssignableFrom(type)) {
          showErrorHint(message("changeSignature.not.throwable.type", typeCodeFragment.getText()));
          return false;
        }
        exceptionInfo.setType((PsiClassType)type);
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        showErrorHint(message("changeSignature.wrong.type.for.exception", typeCodeFragment.getText()));
        return false;
      }
      catch (PsiTypeCodeFragment.NoTypeException e) {
        showErrorHint(message("changeSignature.no.type.for.exception"));
        return false;
      }
    }

    return true;
  }

  private static boolean checkType(PsiTypeCodeFragment typeCodeFragment, boolean allowEllipsis) {
    try {
      final PsiType type = typeCodeFragment.getType();
      return allowEllipsis || !(type instanceof PsiEllipsisType);
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e) {
      return false;
    }
    catch (PsiTypeCodeFragment.NoTypeException e) {
      return true; //Groovy accepts methods and parameters without explicit type
    }
  }
}
