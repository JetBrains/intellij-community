package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class BadExceptionCaughtInspection extends ExpressionInspection {
    public String exceptionCheckString =
            "java.lang.NullPointerException," +
            "java.lang.IllegalMonitorStateException," +
            "java.lang.ArrayOutOfBoundsException";

    private List exceptionsList = new ArrayList(32);

    {
        parseExceptionsString();
    }

    public void readSettings(Element element) throws InvalidDataException {
        super.readSettings(element);
        parseExceptionsString();
    }

    private void parseExceptionsString() {
        exceptionsList.clear();
        final String[] strings = exceptionCheckString.split(",");
        for (int i = 0; i < strings.length; i++ ) {
            exceptionsList.add(strings[i]);
        }
    }

    public void writeSettings(Element element) throws WriteExternalException {
        formatCallCheckString();
        super.writeSettings(element);
    }

    private void formatCallCheckString() {
        final StringBuffer buffer = new StringBuffer();
        boolean first = true;
        for (Iterator iterator = exceptionsList.iterator(); iterator.hasNext();) {
            if (first) {
                first = false;
            } else {
                buffer.append(',');
            }
            final String exceptionName = (String) iterator.next();
            buffer.append(exceptionName);
        }
        exceptionCheckString = buffer.toString();
    }

    public String getID(){
        return "ProhibittedExceptionCaught";
    }
    public String getDisplayName() {
        return "Prohibitted exception caught";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public JComponent createOptionsPanel() {
        final Form form = new Form();
        return form.getContentPanel();
    }

    public String buildErrorString(PsiElement location) {
        return "Prohibitted exception '#ref' caught. #loc ";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new BadExceptionCaughtVisitor(this, inspectionManager, onTheFly);
    }

    private class BadExceptionCaughtVisitor extends BaseInspectionVisitor {
        private BadExceptionCaughtVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitTryStatement(PsiTryStatement statement) {

            super.visitTryStatement(statement);
            final PsiParameter[] catchBlockParameters = statement.getCatchBlockParameters();
            for (int i = 0; i < catchBlockParameters.length; i++) {
                final PsiParameter parameter = catchBlockParameters[i];
                if(parameter == null)
                {
                    continue;
                }
                final PsiType type = parameter.getType();
                if(type == null)
                {
                    continue;
                }
                final String text = type.getCanonicalText();
                if(text == null)
                {
                    continue;
                }
                for (Iterator iterator = exceptionsList.iterator(); iterator.hasNext();) {
                    final String exceptionClass = (String) iterator.next();
                    if (text.equals(exceptionClass)) {
                        final PsiTypeElement typeElement = parameter.getTypeElement();
                        registerError(typeElement);
                        continue;
                    }
                }
            }

        }


    }

    public class Form {
        private JPanel contentPanel;
        private JButton addButton;
        private JButton deleteButton;
        private JTable table;

        public Form() {
            super();
            table.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            table.setRowSelectionAllowed(true);
            table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            table.setEnabled(true);
            final ReturnCheckSpecificationTableModel model =
                    new ReturnCheckSpecificationTableModel();
            table.setModel(model);
            addButton.setEnabled(true);
            addButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    exceptionsList.add("");
                    model.fireTableStructureChanged();
                }
            });
            deleteButton.setEnabled(true);
            deleteButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    final int[] selectedRows = table.getSelectedRows();
                    Arrays.sort(selectedRows);
                    for (int i = selectedRows.length - 1; i >= 0; i--) {
                        exceptionsList.remove(selectedRows[i]);
                    }
                    model.fireTableStructureChanged();
                }
            });
        }

        public JComponent getContentPanel() {
            return contentPanel;
        }
    }

    private class ReturnCheckSpecificationTableModel extends AbstractTableModel {

        public int getRowCount() {
            return exceptionsList.size();
        }

        public int getColumnCount() {
            return 1;
        }

        public String getColumnName(int columnIndex) {
            return "Exception class";
        }

        public Class getColumnClass(int columnIndex) {
            return String.class;
        }

        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        public Object getValueAt(int rowIndex, int columnIndex) {
            return exceptionsList.get(rowIndex);
        }

        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            exceptionsList.set(rowIndex, aValue);
        }
    }

}
