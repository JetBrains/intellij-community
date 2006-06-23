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
package com.siyeh.ig.naming;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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

public class BooleanMethodNameMustStartWithQuestionInspection
        extends MethodInspection{

    /** @noinspection PublicField*/
    @NonNls public String nameCheckString =
            "is,can,has,should,could,will,shall,check,contains,equals,add,put," +
                    "remove,startsWith,endsWith";
    private final RenameFix fix = new RenameFix();

    private List<Object> nameList = new ArrayList<Object>(32);
    private final Object lock = new Object();

    {
        parseNameString();
    }

    public void readSettings(Element element) throws InvalidDataException{
        super.readSettings(element);
        parseNameString();
    }

    private void parseNameString(){
        final String[] strings = nameCheckString.split(",");
        synchronized(lock) {
            nameList.clear();
            for(String string : strings){
                nameList.add(string);
            }
        }
    }

    public void writeSettings(Element element) throws WriteExternalException{
        formatNameCheckString();
        super.writeSettings(element);
    }

    private void formatNameCheckString(){
        final StringBuilder buffer = new StringBuilder();
        synchronized(lock) {
            boolean first = true;
            for(Object aNameList : nameList){
                if(first){
                    first = false;
                } else{
                    buffer.append(',');
                }
                final String exceptionName = (String) aNameList;
                buffer.append(exceptionName);
            }
        }
        nameCheckString = buffer.toString();
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "boolean.method.name.must.start.with.question.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    public JComponent createOptionsPanel(){
        final Form form = new Form();
        return form.getContentPanel();
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "boolean.method.name.must.start.with.question.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new QuestionableNameVisitor();
    }

    private class QuestionableNameVisitor extends BaseInspectionVisitor{

        public void visitMethod(@NotNull PsiMethod method){
            final PsiType returnType = method.getReturnType();
            if(returnType== null){
                return;
            }
            if(!returnType.equals(PsiType.BOOLEAN)){
                return;
            }
            final String name = method.getName();
            synchronized(lock) {
                for(Object aNameList : nameList){
                    final String prefix = (String) aNameList;
                    if(name.startsWith(prefix)){
                        return;
                    }
                }
            }
            if(LibraryUtil.isOverrideOfLibraryMethod(method)){
                return;
            }
            registerMethodError(method);
        }
    }

    private class Form{

        JPanel contentPanel;
        JButton addButton;
        JButton deleteButton;
        JTable table;

        Form(){
            super();
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            table.setRowSelectionAllowed(true);
            table.setSelectionMode(
                    ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            final NameListTableModel model =
            new NameListTableModel();
            table.setModel(model);
            addButton.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e){
                    final int listSize;
                    synchronized(lock){
                        listSize = nameList.size();
                        nameList.add("");
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
                            nameList.remove(selectedRows[i]);
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
        }

        public JComponent getContentPanel(){
            return contentPanel;
        }

    }

    private class NameListTableModel extends AbstractTableModel{

        public int getRowCount(){
            synchronized (lock) {
                return nameList.size();
            }
        }

        public int getColumnCount(){
            return 1;
        }

        public String getColumnName(int columnIndex){
            return InspectionGadgetsBundle.message(
                    "boolean.method.name.must.start.with.question.table.column.name");
        }

        public Class getColumnClass(int columnIndex){
            return String.class;
        }

        public boolean isCellEditable(int rowIndex, int columnIndex){
            return true;
        }

        public Object getValueAt(int rowIndex, int columnIndex){
            synchronized (lock) {
                return nameList.get(rowIndex);
            }
        }

        public void setValueAt(Object aValue, int rowIndex, int columnIndex){
            synchronized (lock) {
                nameList.set(rowIndex, aValue);
            }
        }
    }
}