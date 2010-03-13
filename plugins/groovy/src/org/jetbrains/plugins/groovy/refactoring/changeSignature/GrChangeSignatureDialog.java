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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiTypeCodeFragment;
import com.intellij.refactoring.ui.CodeFragmentTableCellEditor;
import com.intellij.refactoring.ui.CodeFragmentTableCellRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ui.Table;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
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

/**
 * @author Maxim.Medvedev
 */
public class GrChangeSignatureDialog extends DialogWrapper {
  private EditorTextField myNameField;
  private EditorTextField myReturnTypeField;
  private JRadioButton myPublicRadioButton;

  private JRadioButton myProtectedRadioButton;
  private JRadioButton myPrivateRadioButton1;
  private JPanel myParametersPanel;
  private Table myParameterTable;
  private JButton myAddButton;
  private JButton myRemoveButton;
  private JButton myMoveUpButton;
  private JButton myMoveDownButton;
  private JPanel contentPane;
  private JLabel mySignatureLabel;
  private GrParameterTableModel myParameterModel;
  private GrMethod myMethod;
  private Project myProject;
  private PsiTypeCodeFragment myReturnTypeCodeFragment;

  public GrChangeSignatureDialog(@NotNull Project project, GrMethod method) {
    super(project, true);
    myProject = project;
    myMethod = method;
    init();
    configureParameterButtons();
    updateSignature();
  }

  private void configureParameterButtons() {
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int selectedColumn = myParameterTable.getSelectedColumn();
        myParameterModel.addRow();
        myParameterTable.setRowSelectionInterval(myParameterModel.getRowCount() - 1, myParameterModel.getRowCount() - 1);
        myParameterTable.setColumnSelectionInterval(selectedColumn, selectedColumn);
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
      }
    });

    myMoveUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final int selectedRow = myParameterTable.getSelectedRow();
        int selectedColumn = myParameterTable.getSelectedColumn();
        myParameterModel.exchangeRows(selectedRow, selectedRow - 1);
        myParameterTable.setRowSelectionInterval(selectedRow - 1, selectedRow - 1);
        myParameterTable.setColumnSelectionInterval(selectedColumn, selectedColumn);
      }
    });

    myMoveDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final int selectedRow = myParameterTable.getSelectedRow();
        int selectedColumn = myParameterTable.getSelectedColumn();
        myParameterModel.exchangeRows(selectedRow, selectedRow + 1);
        myParameterTable.setRowSelectionInterval(selectedRow + 1, selectedRow + 1);
        myParameterTable.setColumnSelectionInterval(selectedColumn, selectedColumn);
      }
    });
  }

  protected void init() {
    super.init();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

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
    myNameField = new EditorTextField("", myProject, GroovyFileType.GROOVY_FILE_TYPE);
    myReturnTypeCodeFragment = JavaPsiFacade.getInstance(myProject).getElementFactory().createTypeCodeFragment("", myMethod, true, true);
    final Document document = PsiDocumentManager.getInstance(myProject).getDocument(myReturnTypeCodeFragment);
    myReturnTypeField = new EditorTextField(document, myProject, myReturnTypeCodeFragment.getFileType());

    myNameField.setText(myMethod.getName());
    final GrTypeElement element = myMethod.getReturnTypeElementGroovy();
    if (element != null) {
      myReturnTypeField.setText(element.getText());
    }
  }

  private void createParametersModel() {
    myParameterModel = new GrParameterTableModel(myMethod, this, myProject);
    myParameterTable = new Table(myParameterModel);
    myParameterTable.setCellSelectionEnabled(true);

    myParameterTable.getColumnModel().getColumn(0).setCellRenderer(new CodeFragmentTableCellRenderer(myProject));
    myParameterTable.getColumnModel().getColumn(1).setCellRenderer(new GrCodeFragmentTableCellRenderer(myProject));
    myParameterTable.getColumnModel().getColumn(2).setCellRenderer(new GrCodeFragmentTableCellRenderer(myProject));
    myParameterTable.getColumnModel().getColumn(3).setCellRenderer(new GrCodeFragmentTableCellRenderer(myProject));

    myParameterTable.getColumnModel().getColumn(0).setCellEditor(new CodeFragmentTableCellEditor(myProject));
    myParameterTable.getColumnModel().getColumn(1).setCellEditor(new GrCodeFragmentTableCellEditor(myProject));
    myParameterTable.getColumnModel().getColumn(2).setCellEditor(new GrCodeFragmentTableCellEditor(myProject));
    myParameterTable.getColumnModel().getColumn(3).setCellEditor(new GrCodeFragmentTableCellEditor(myProject));

    myParameterTable.setRowSelectionInterval(0, 0);
    myParameterTable.setColumnSelectionInterval(0, 0);

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
    String name = myNameField.getText().trim();
    String type = myReturnTypeField.getText().trim();
    if (type.length() == 0) {
      type = GrModifier.DEF;
    }

    StringBuilder builder = new StringBuilder();
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
    final PsiTypeCodeFragment typeFragment = info.getType();
    builder.append(typeFragment != null ? typeFragment.getText().trim() : GrModifier.DEF).append(' ');
    final GroovyCodeFragment nameFragment = info.getName();
    builder.append(nameFragment != null ? nameFragment.getText().trim() : "");
    final GroovyCodeFragment defaultInitializer = info.getDefaultInitializer();

    final String defaultInitializerText = defaultInitializer != null ? defaultInitializer.getText().trim() : "";
    if (defaultInitializerText.length() > 0) {
      builder.append(" = ").append(defaultInitializerText);
    }
  }
}