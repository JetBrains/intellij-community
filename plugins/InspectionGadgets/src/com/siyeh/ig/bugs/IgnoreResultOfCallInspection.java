/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IgnoreResultOfCallInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public boolean m_reportAllNonLibraryCalls = false;

    /** @noinspection PublicField*/
    @NonNls public String callCheckString = "java.io.InputStream,read," +
        "java.io.InputStream,skip," +
        "java.lang.StringBuffer,toString," +
        "java.lang.StringBuilder,toString," +
        "java.lang.String,.*," +
        "java.math.BigInteger,.*," +
        "java.math.BigDecimal,.*," +
        "java.net.InetAddress,.*";

    final List<ReturnCheckSpecification> callsToCheck =
            new ArrayList<ReturnCheckSpecification>(32);
    final Object lock = new Object();

    public IgnoreResultOfCallInspection(){
        parseCallCheckString();
    }

    public void readSettings(Element element) throws InvalidDataException{
        super.readSettings(element);
        parseCallCheckString();
    }

    private void parseCallCheckString(){
        final String[] strings = callCheckString.split(",");
        synchronized(lock){
            callsToCheck.clear();
            for(int i = 0; i < strings.length-1; i += 2){
                final String className = strings[i];
                final String methodName = strings[i + 1];
                callsToCheck.add(
                        new ReturnCheckSpecification(className, methodName));
            }
        }
    }

    public void writeSettings(Element element) throws WriteExternalException{
        formatCallCheckString();
        super.writeSettings(element);
    }

    private void formatCallCheckString(){
        final StringBuilder buffer = new StringBuilder();
        synchronized(lock){

            boolean first=true;
            for(ReturnCheckSpecification returnCheckSpecification :
                    callsToCheck){
                if(first){
                    first = false;
                } else{
                    buffer.append(',');
                }
                final String methodName =
                        returnCheckSpecification.getMethodName();
                final String className =
                        returnCheckSpecification.getClassName();
                buffer.append(className);
                buffer.append(',');
                buffer.append(methodName);
            }
        }
        callCheckString = buffer.toString();
    }

    @NotNull
    public String getID(){
        return "ResultOfMethodCallIgnored";
    }

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "result.of.method.call.ignored.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        final PsiClass containingClass = (PsiClass)infos[0];
        final String className = containingClass.getName();
        return InspectionGadgetsBundle.message(
                "result.of.method.call.ignored.problem.descriptor",
                className);
    }

    public JComponent createOptionsPanel(){
        final Form form = new Form();
        return form.getContentPanel();
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new IgnoreResultOfCallVisitor();
    }

    private class IgnoreResultOfCallVisitor extends BaseInspectionVisitor{

        public void visitExpressionStatement(
                @NotNull PsiExpressionStatement statement){
            super.visitExpressionStatement(statement);
            final PsiExpression expression = statement.getExpression();
            if(!(expression instanceof PsiMethodCallExpression)){
                return;
            }
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression) expression;
            final PsiMethod method = call.resolveMethod();
            if(method == null){
                return;
            }
            if(method.isConstructor()){
                return;
            }
            final PsiType retType = method.getReturnType();
            if(PsiType.VOID.equals(retType)){
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null){
                return;
            }
            if(m_reportAllNonLibraryCalls &&
                       !LibraryUtil.classIsInLibrary(aClass)){
                registerMethodCallError(call, aClass);
                return;
            }
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if(methodName == null){
                return;
            }
            final List<ReturnCheckSpecification> callsToCheckCopy;
            synchronized(lock){
                callsToCheckCopy =
                        new ArrayList<ReturnCheckSpecification>(callsToCheck);
            }
            for(ReturnCheckSpecification spec : callsToCheckCopy){
                final Pattern methodNamePattern = spec.getMethodNamePattern();
                if(methodNamePattern != null &&
                        methodNamesMatch(methodName, methodNamePattern)){
                    final String classNameToCompare = spec.getClassName();
                    if(ClassUtils.isSubclass(aClass, classNameToCompare)){
                        registerMethodCallError(call, aClass);
                        return;
                    }
                }
            }
        }

        private boolean methodNamesMatch(String methodName,
                                         Pattern methodNamePattern){
            final Matcher matcher = methodNamePattern.matcher(methodName);
            return matcher.matches();
        }
    }

    private class Form{

        JPanel contentPanel;
        JButton addButton;
        JButton deleteButton;
        JTable table;
        JCheckBox nonLibraryCheckbox;

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
                        listSize = callsToCheck.size();
                        callsToCheck.add(new ReturnCheckSpecification());
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
                            callsToCheck.remove(selectedRows[i]);
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
            nonLibraryCheckbox.setEnabled(true);
            nonLibraryCheckbox.setSelected(m_reportAllNonLibraryCalls);
            final ButtonModel buttonModel = nonLibraryCheckbox.getModel();
            buttonModel.addChangeListener(new ChangeListener(){
                public void stateChanged(ChangeEvent e){
                    m_reportAllNonLibraryCalls = buttonModel.isSelected();
                }
            });
        }

        public JComponent getContentPanel(){
            return contentPanel;
        }

    }

    private class ReturnCheckSpecificationTableModel
            extends AbstractTableModel{

        public int getRowCount(){
            synchronized(lock){
                return callsToCheck.size();
            }
        }

        public int getColumnCount(){
            return 2;
        }

        public String getColumnName(int columnIndex){
            if(columnIndex == 0){
                return InspectionGadgetsBundle.message(
                        "result.of.method.call.ignored.class.column.title");
            }
            return InspectionGadgetsBundle.message(
                    "result.of.method.call.ignored.method.column.title");
        }

        public Class getColumnClass(int columnIndex){
            return String.class;
        }

        public boolean isCellEditable(int rowIndex, int columnIndex){
            return true;
        }

        public Object getValueAt(int rowIndex, int columnIndex){
            final ReturnCheckSpecification spec;
            synchronized(lock){
                spec = callsToCheck.get(rowIndex);
            }
            if(columnIndex == 0){
                return spec.getClassName();
            } else{
                return spec.getMethodName();
            }
        }

        public void setValueAt(Object aValue, int rowIndex, int columnIndex){
            final ReturnCheckSpecification spec;
            synchronized(lock){
                spec = callsToCheck.get(rowIndex);
            }
            if(columnIndex == 0){
                spec.setClassName((String) aValue);
            } else{
                spec.setMethodName((String) aValue);
            }
        }
    }
}