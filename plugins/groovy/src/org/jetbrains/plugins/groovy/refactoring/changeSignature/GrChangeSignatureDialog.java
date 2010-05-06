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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCodeFragment;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.ui.CodeFragmentTableCellEditor;
import com.intellij.refactoring.ui.CodeFragmentTableCellRenderer;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.refactoring.ui.GrCodeFragmentTableCellEditor;
import org.jetbrains.plugins.groovy.refactoring.ui.GrCodeFragmentTableCellRenderer;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
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
  private JPanel myParametersPanel;
  private JBTable myParameterTable;
  private JButton myAddButton;
  private JButton myRemoveButton;
  private JButton myMoveUpButton;
  private JButton myMoveDownButton;
  private JPanel contentPane;
  private JLabel mySignatureLabel;
  private JLabel myNameLabel;
  private JLabel myReturnTypeLabel;
  private GrParameterTableModel myParameterModel;
  private GrMethod myMethod;
//  private Project myProject;
  private PsiTypeCodeFragment myReturnTypeCodeFragment;
  private GroovyCodeFragment myNameCodeFragment;

  public GrChangeSignatureDialog(@NotNull Project project, GrMethod method) {
    super(project, true);
    myMethod = method;
    init();
    configureParameterButtons();
    updateSignature();
  }

  private void configureParameterButtons() {
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int selectedColumn = myParameterTable.getSelectedColumn();
        if (selectedColumn < 0) selectedColumn = 0;
        myParameterModel.addRow();
        myParameterTable.setRowSelectionInterval(myParameterModel.getRowCount() - 1, myParameterModel.getRowCount() - 1);
        myParameterTable.setColumnSelectionInterval(selectedColumn, selectedColumn);
        updateSignature();
        myParameterTable.requestFocus();
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int selectedRow = myParameterTable.getSelectedRow();
        int selectedColumn = myParameterTable.getSelectedColumn();

        myParameterModel.removeRow(myParameterTable.getSelectedRow());

        if (selectedRow == myParameterModel.getRowCount()) selectedRow--;
        if (myParameterModel.getRowCount() == 0) return;
        myParameterTable.setRowSelectionInterval(selectedRow, selectedRow);
        myParameterTable.setColumnSelectionInterval(selectedColumn, selectedColumn);
        updateSignature();
        myParameterTable.requestFocus();
      }
    });

    myMoveUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final int selectedRow = myParameterTable.getSelectedRow();
        int selectedColumn = myParameterTable.getSelectedColumn();
        myParameterModel.exchangeRows(selectedRow, selectedRow - 1);
        myParameterTable.setRowSelectionInterval(selectedRow - 1, selectedRow - 1);
        myParameterTable.setColumnSelectionInterval(selectedColumn, selectedColumn);
        updateSignature();
        myParameterTable.requestFocus();
      }
    });

    myMoveDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final int selectedRow = myParameterTable.getSelectedRow();
        int selectedColumn = myParameterTable.getSelectedColumn();
        myParameterModel.exchangeRows(selectedRow, selectedRow + 1);
        myParameterTable.setRowSelectionInterval(selectedRow + 1, selectedRow + 1);
        myParameterTable.setColumnSelectionInterval(selectedColumn, selectedColumn);
        updateSignature();
        myParameterTable.requestFocus();
      }
    });
  }

  protected void init() {
    super.init();
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
    createParametersModel();
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

  private void createParametersModel() {
    myParameterModel = new GrParameterTableModel(myMethod, this, myProject);
    myParameterTable = new JBTable(myParameterModel);
    myParameterTable.setCellSelectionEnabled(true);

    myParameterTable.getColumnModel().getColumn(0).setCellRenderer(new CodeFragmentTableCellRenderer(myProject));
    myParameterTable.getColumnModel().getColumn(1).setCellRenderer(new GrCodeFragmentTableCellRenderer(myProject));
    myParameterTable.getColumnModel().getColumn(2).setCellRenderer(new GrCodeFragmentTableCellRenderer(myProject));
    myParameterTable.getColumnModel().getColumn(3).setCellRenderer(new GrCodeFragmentTableCellRenderer(myProject));

    myParameterTable.getColumnModel().getColumn(0).setCellEditor(new CodeFragmentTableCellEditor(myProject));
    myParameterTable.getColumnModel().getColumn(1).setCellEditor(new GrCodeFragmentTableCellEditor(myProject));
    myParameterTable.getColumnModel().getColumn(2).setCellEditor(new GrCodeFragmentTableCellEditor(myProject));
    myParameterTable.getColumnModel().getColumn(3).setCellEditor(new GrCodeFragmentTableCellEditor(myProject));

    if (myParameterTable.getRowCount() > 0) {
      myParameterTable.setRowSelectionInterval(0, 0);
      myParameterTable.setColumnSelectionInterval(0, 0);
    }

    myParameterModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        updateSignature();
      }
    });

  }

  private void updateSignature() {
    final int selectedRow = myParameterTable.getSelectedRow();
    final int rowCount = myParameterModel.getRowCount();
    myMoveUpButton.setEnabled(selectedRow > 0);
    myMoveDownButton.setEnabled(selectedRow + 1 < rowCount && rowCount > 1);
    myRemoveButton.setEnabled(rowCount > 0);

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
    final List<GrParameterInfo> infos = myParameterModel.getParameterInfos();
    for (int i = 0, infosSize = infos.size() - 1; i < infosSize; i++) {
      generateParameterText(infos.get(i), builder);
      builder.append(", ");
    }
    if (infos.size() > 0) {
      generateParameterText(infos.get(infos.size() - 1), builder);
    }

    builder.append(')');
    return builder.toString();
  }


  private static void generateParameterText(GrParameterInfo info, StringBuilder builder) {
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
    catch (PsiTypeCodeFragment.TypeSyntaxException e) {
    }
    catch (PsiTypeCodeFragment.NoTypeException e) {
    }

    String newName = getNewName();
    final List<GrParameterInfo> parameterInfos = myParameterModel.getParameterInfos();
    invokeRefactoring(new GrChangeSignatureProcessor(myProject, new GrChangeInfoImpl(myMethod, modifier,
                                                                                     returnType == null
                                                                                     ? null
                                                                                     : CanonicalTypes.createTypeWrapper(
                                                                                       returnType), newName,
                                                                                     parameterInfos)));

  }

  private String getNewName() {
    return myNameField.getText().trim();
  }

  private boolean validateInputData() {
    if (!StringUtil.isJavaIdentifier(getNewName())) {
      CommonRefactoringUtil
        .showErrorHint(myProject, null, message("name.is.wrong", getNewName()), message("incorrect.data"), HelpID.CHANGE_SIGNATURE);
      return false;
    }

    if (!checkType(myReturnTypeCodeFragment)) {
      CommonRefactoringUtil
        .showErrorHint(myProject, null, message("return.type.is.wrong"), message("incorrect.data"), HelpID.CHANGE_SIGNATURE);
      return false;
    }

    for (GrParameterInfo info : myParameterModel.getParameterInfos()) {
      if (!StringUtil.isJavaIdentifier(info.getName())) {
        CommonRefactoringUtil
          .showErrorHint(myProject, null, message("name.is.wrong", info.getName()), message("incorrect.data"), HelpID.CHANGE_SIGNATURE);
      }
      if (!checkType(info.getTypeFragment())) {
        CommonRefactoringUtil
          .showErrorHint(myProject, null, message("type.for.parameter.is.incorrect", info.getName()), message("incorrect.data"),
                         HelpID.CHANGE_SIGNATURE);
        return false;
      }
      String defaultValue = info.getDefaultValue();
      if (info.getOldIndex() < 0 && (defaultValue == null || defaultValue.trim().length() == 0)) {
        CommonRefactoringUtil.showErrorHint(myProject, null, message("specify.default.value", info.getName()), message("incorrect.data"),
                                            HelpID.CHANGE_SIGNATURE);
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