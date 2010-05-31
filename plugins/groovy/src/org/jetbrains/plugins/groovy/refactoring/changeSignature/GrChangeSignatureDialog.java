/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.changeSignature.ExceptionsTableModel;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.ui.CodeFragmentTableCellEditor;
import com.intellij.refactoring.ui.CodeFragmentTableCellRenderer;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.EditableRowTable;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.ui.GrCodeFragmentTableCellEditor;
import org.jetbrains.plugins.groovy.refactoring.ui.GrCodeFragmentTableCellRenderer;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import static org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle.message;

/**
 * @author Maxim.Medvedev
 */
public class GrChangeSignatureDialog extends RefactoringDialog {
  private EditorTextField myNameField;
  private EditorTextField myReturnTypeField;
  private JRadioButton myPublicRadioButton;
  private JRadioButton myProtectedRadioButton;
  private JRadioButton myPrivateRadioButton;
  private JBTable myParameterTable;
  private JPanel contentPane;
  private JTextArea mySignatureLabel;
  private JLabel myNameLabel;
  private JLabel myReturnTypeLabel;
  @SuppressWarnings({"UnusedDeclaration"}) private JRadioButton myModifyRadioButton;
  private JRadioButton myDelegateRadioButton;
  @SuppressWarnings({"UnusedDeclaration"}) private JPanel myParameterButtonPanel;
  private JBTable myExceptionsTable;
  @SuppressWarnings({"UnusedDeclaration"}) private JPanel myExceptionsButtonPanel;
  private JPanel myDelegatePanel;
  private GrParameterTableModel myParameterModel;
  private GrMethod myMethod;
  private PsiTypeCodeFragment myReturnTypeCodeFragment;
  private GroovyCodeFragment myNameCodeFragment;
  private ExceptionsTableModel myExceptionTableModel;
  private static final String INDENT = "    ";

  public GrChangeSignatureDialog(@NotNull Project project, GrMethod method) {
    super(project, true);
    myMethod = method;
    init();
    updateSignature();
    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateSignature();
      }
    };
    myPublicRadioButton.addActionListener(listener);
    myPrivateRadioButton.addActionListener(listener);
    myProtectedRadioButton.addActionListener(listener);
  }

  protected void init() {
    super.init();
    final PsiClass psiClass = myMethod.getContainingClass();
    if (psiClass == null) return;
    if (psiClass.isInterface()) {
      myDelegatePanel.setVisible(false);
    }
  }

  private void stopEditing() {
    TableUtil.stopEditing(myParameterTable);
  }

  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }


  private void createUIComponents() {
    createNameAndReturnTypeEditors();
    createParametersPanel();
    createExceptionsPanel();
  }

  private void createNameAndReturnTypeEditors() {
    myNameCodeFragment = new GroovyCodeFragment(myProject, "");
    myNameField = new EditorTextField(PsiDocumentManager.getInstance(myProject).getDocument(myNameCodeFragment), myProject,
                                      myNameCodeFragment.getFileType());

    myReturnTypeCodeFragment = JavaPsiFacade.getInstance(myProject).getElementFactory().createTypeCodeFragment("", myMethod, true, true);
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
  }

  private void createParametersPanel() {
    myParameterModel = new GrParameterTableModel(myMethod, this, myProject);
    myParameterModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        updateSignature();
      }
    });
    myParameterTable = new JBTable(myParameterModel);
    myParameterTable.setPreferredScrollableViewportSize(new Dimension(550, myParameterTable.getRowHeight() * 8));

    myParameterButtonPanel = EditableRowTable.createButtonsTable(myParameterTable, myParameterModel, true);

    myParameterTable.setCellSelectionEnabled(true);
    final TableColumnModel columnModel = myParameterTable.getColumnModel();
    columnModel.getColumn(0).setCellRenderer(new CodeFragmentTableCellRenderer(myProject));
    columnModel.getColumn(1).setCellRenderer(new GrCodeFragmentTableCellRenderer(myProject));
    columnModel.getColumn(2).setCellRenderer(new GrCodeFragmentTableCellRenderer(myProject));
    columnModel.getColumn(3).setCellRenderer(new GrCodeFragmentTableCellRenderer(myProject));

    columnModel.getColumn(0).setCellEditor(new CodeFragmentTableCellEditor(myProject));
    columnModel.getColumn(1).setCellEditor(new GrCodeFragmentTableCellEditor(myProject));
    columnModel.getColumn(2).setCellEditor(new GrCodeFragmentTableCellEditor(myProject));
    columnModel.getColumn(3).setCellEditor(new GrCodeFragmentTableCellEditor(myProject));

    if (myParameterModel.getRowCount() > 0) {
      myParameterTable.setRowSelectionInterval(0, 0);
      myParameterTable.setColumnSelectionInterval(0, 0);
    }
  }

  private void createExceptionsPanel() {
    myExceptionTableModel = new ExceptionsTableModel(myMethod);
    myExceptionTableModel.setTypeInfos(myMethod);
    myExceptionTableModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        updateSignature();
      }
    });
    myExceptionsTable = new JBTable(myExceptionTableModel);
    myExceptionsTable.setPreferredScrollableViewportSize(new Dimension(200, myExceptionsTable.getRowHeight() * 8));

    myExceptionsButtonPanel = EditableRowTable.createButtonsTable(myExceptionsTable, myExceptionTableModel, false);

    myExceptionsTable.getColumnModel().getColumn(0).setCellRenderer(new CodeFragmentTableCellRenderer(myProject));
    myExceptionsTable.getColumnModel().getColumn(0).setCellEditor(new CodeFragmentTableCellEditor(myProject));

    if (myExceptionTableModel.getRowCount() > 0) {
      myExceptionsTable.setRowSelectionInterval(0, 0);
      myExceptionsTable.setColumnSelectionInterval(0, 0);
    }

  }


  private void updateSignature() {
    mySignatureLabel.setText(generateSignatureText());
  }

  private String generateSignatureText() {
    String name = getNewName();
    String type = myReturnTypeField.getText().trim();

    StringBuilder builder = new StringBuilder();
    if (myPublicRadioButton.isSelected() && type.length() == 0) {
      builder.append(GrModifier.DEF);
    }
    if (myPrivateRadioButton.isSelected()) {
      builder.append(GrModifier.PRIVATE).append(' ');
    }
    else if (myProtectedRadioButton.isSelected()) {
      builder.append(GrModifier.PROTECTED).append(' ');
    }
    builder.append(type).append(' ');
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

    stopEditing();
    String modifier = "";
    if (myPublicRadioButton.isSelected()) {
      modifier = GrModifier.PUBLIC;
    }
    else if (myPrivateRadioButton.isSelected()) {
      modifier = GrModifier.PRIVATE;
    }
    else if (myProtectedRadioButton.isSelected()) {
      modifier = GrModifier.PROTECTED;
    }

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
    invokeRefactoring(new GrChangeSignatureProcessor(myProject, new GrChangeInfoImpl(myMethod, modifier, returnType == null
                                                                                                         ? null
                                                                                                         : CanonicalTypes
                                                                                                           .createTypeWrapper(returnType),
                                                                                     newName, parameterInfos, exceptionInfos,
                                                                                     myDelegateRadioButton.isSelected())));
  }

  private String getNewName() {
    return myNameField.getText().trim();
  }

  private void showErrorHint(String hint) {
    CommonRefactoringUtil.showErrorHint(myProject, null, hint, GroovyRefactoringBundle.message("incorrect.data"), HelpID.CHANGE_SIGNATURE);
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

    if (!checkType(myReturnTypeCodeFragment)) {
      showErrorHint(message("return.type.is.wrong"));
      return false;
    }

    for (GrTableParameterInfo info : myParameterModel.getParameterInfos()) {
      if (!StringUtil.isJavaIdentifier(info.getName())) {
        showErrorHint(message("name.is.wrong", info.getName()));
        return false;
      }
      if (!checkType(info.getTypeFragment())) {
        showErrorHint(message("type.for.parameter.is.incorrect", info.getName()));
        return false;
      }
      String defaultValue = info.getDefaultValue();
      if (info.getOldIndex() < 0 && (defaultValue == null || defaultValue.trim().length() == 0)) {
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
          showErrorHint(GroovyRefactoringBundle.message("changeSignature.wrong.type.for.exception", typeCodeFragment.getText()));
          return false;
        }

        PsiClassType throwable = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory()
          .createTypeByFQClassName("java.lang.Throwable", type.getResolveScope());
        if (!throwable.isAssignableFrom(type)) {
          showErrorHint(GroovyRefactoringBundle.message("changeSignature.not.throwable.type", typeCodeFragment.getText()));
          return false;
        }
        exceptionInfo.setType((PsiClassType)type);
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        showErrorHint(GroovyRefactoringBundle.message("changeSignature.wrong.type.for.exception", typeCodeFragment.getText()));
        return false;
      }
      catch (PsiTypeCodeFragment.NoTypeException e) {
        showErrorHint(GroovyRefactoringBundle.message("changeSignature.no.type.for.exception"));
        return false;
      }
    }

    return true;
  }

  private static boolean checkType(PsiTypeCodeFragment typeCodeFragment) {
    try {
      typeCodeFragment.getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e) {
      return false;
    }
    catch (PsiTypeCodeFragment.NoTypeException e) {
      return true; //Groovy accepts methods and parameters without explicit type
    }
    return true;
  }

}