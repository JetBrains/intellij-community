/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class BadExceptionDeclaredInspection extends MethodInspection{

    /** @noinspection PublicField*/
    public String exceptionCheckString =
      "java.lang.Throwable" + "," +
      "java.lang.Exception" + "," +
      "java.lang.Error" + "," +
      "java.lang.RuntimeException" + "," +
      "java.lang.NullPointerException" + "," +
      "java.lang.ClassCastException" + "," +
      "java.lang.ArrayIndexOutOfBoundsException";

    /** @noinspection PublicField*/
    public boolean ignoreTestCases = false;
    private final List<String> exceptionList = new ArrayList<String>(32);
    private final Object lock = new Object();

    public BadExceptionDeclaredInspection() {
        parseCallCheckString();
    }

    public void readSettings(Element element) throws InvalidDataException{
        super.readSettings(element);
        parseCallCheckString();
    }

    private void parseCallCheckString(){
        final String[] strings = exceptionCheckString.split(",");
        synchronized(lock){
            exceptionList.clear();
            for(String string : strings){
                exceptionList.add(string);
            }
        }
    }

    public void writeSettings(Element element) throws WriteExternalException{
        formatCallCheckString();
        super.writeSettings(element);
    }

    private void formatCallCheckString(){
        final StringBuffer buffer = new StringBuffer();
        synchronized(lock){
            boolean first = true;
            for(String exceptionName : exceptionList){
                if(first){
                    first = false;
                } else{
                    buffer.append(',');
                }
                buffer.append(exceptionName);
            }
        }
        exceptionCheckString = buffer.toString();
    }

    public String getID(){
        return "ProhibitedExceptionDeclared";
    }

    public String getGroupDisplayName(){
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "bad.exception.declared.problem.descriptor");
    }

    public JComponent createOptionsPanel(){
        final Form form = new Form();
        return form.getContentPanel();
    }

    public BaseInspectionVisitor buildVisitor(){
        return new BadExceptionDeclaredVisitor();
    }

    private class BadExceptionDeclaredVisitor extends BaseInspectionVisitor{

        public void visitMethod(@NotNull PsiMethod method){
            super.visitMethod(method);
            if(ignoreTestCases){
                final PsiClass containingClass = method.getContainingClass();
                if(ClassUtils.isSubclass(containingClass,
                        "junit.framework.Test")){
                    return;
                }
            }
            final PsiReferenceList throwsList = method.getThrowsList();
            final PsiJavaCodeReferenceElement[] references =
                    throwsList.getReferenceElements();
            final Set<String> exceptionListCopy;
            synchronized(lock){
                exceptionListCopy = new HashSet<String>(exceptionList);
            }
            for(PsiJavaCodeReferenceElement reference : references){
                final PsiElement element = reference.resolve();
                if (!(element instanceof PsiClass)) {
                    continue;
                }
                final PsiClass thrownClass = (PsiClass)element;
                final String qualifiedName = thrownClass.getQualifiedName();
                if (qualifiedName != null &&
                        exceptionListCopy.contains(qualifiedName)) {
                    registerError(reference);
                }
            }
        }
    }

    private class Form{
        JPanel contentPanel;
        JButton addButton;
        JButton deleteButton;
        JCheckBox ignoreTestCasesCheckBox;
        JTable table;

        Form(){
            super();
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            table.setRowSelectionAllowed(true);
            table.setSelectionMode(
                    ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            final ReturnCheckSpecificationTableModel model =
                    new ReturnCheckSpecificationTableModel();
            table.setModel(model);
            addButton.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e){
                    final int listSize;
                    synchronized(lock){
                        listSize = exceptionList.size();
                        exceptionList.add("");
                    }
                    model.fireTableStructureChanged();

                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            final Rectangle rect =
                                    table.getCellRect(listSize, 0, true);
                            table.scrollRectToVisible(rect);
                            table.editCellAt(listSize, 0);
                            final TableCellEditor editor =
                                    table.getCellEditor();
                            final Component component =
                                    editor.getTableCellEditorComponent(table,
                                            null, true, listSize, 0);
                            component.requestFocus();
                        }
                    });
                }
            });
            deleteButton.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e){
                    final int[] selectedRows = table.getSelectedRows();
                    if (selectedRows.length == 0) {
                        return;
                    }
                    final int row = selectedRows[selectedRows.length - 1] - 1;
                    Arrays.sort(selectedRows);
                    synchronized(lock){
                        for(int i = selectedRows.length - 1; i >= 0; i--){
                            exceptionList.remove(selectedRows[i]);
                        }
                    }
                    model.fireTableStructureChanged();
                    final int count = table.getRowCount();
                    if (count <= row) {
                        table.setRowSelectionInterval(count - 1, count - 1);
                    } else {
                        table.setRowSelectionInterval(row, row);
                    }
                }
            });
            ignoreTestCasesCheckBox.setSelected(ignoreTestCases);
            final ButtonModel buttonModel = ignoreTestCasesCheckBox.getModel();
            buttonModel.addChangeListener(new ChangeListener(){
                public void stateChanged(ChangeEvent e){
                    ignoreTestCases = buttonModel.isSelected();
                }
            });
        }

        public JComponent getContentPanel(){
            return contentPanel;
        }
    }

    private class ReturnCheckSpecificationTableModel extends AbstractTableModel{

        public int getRowCount(){
            synchronized(lock){
                return exceptionList.size();
            }
        }

        public int getColumnCount(){
            return 1;
        }

        public String getColumnName(int columnIndex){
            return InspectionGadgetsBundle.message(
                    "exception.class.column.name");
        }

        public Class<?> getColumnClass(int columnIndex){
            return String.class;
        }

        public boolean isCellEditable(int rowIndex, int columnIndex){
            return true;
        }

        public Object getValueAt(int rowIndex, int columnIndex){
            synchronized(lock){
                return exceptionList.get(rowIndex);
            }
        }

        public void setValueAt(Object aValue, int rowIndex, int columnIndex){
            synchronized(lock){
                exceptionList.set(rowIndex, (String) aValue);
            }
        }
    }
}