package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.ig.*;
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

public class BadExceptionDeclaredInspection extends MethodInspection {
    public String exceptionCheckString = "java.lang.Throwable," +
            "java.lang.Exception," +
            "java.lang.Error," +
            "java.lang.RuntimeException," +
            "java.lang.NullPointerException," +
            "java.lang.ClassCastException," +
            "java.lang.ArrayOutOfBoundsException";

    private List exceptionsList = new ArrayList(32);

    {
        parseCallCheckString();
    }

    public void readSettings(Element element) throws InvalidDataException {
        super.readSettings(element);
        parseCallCheckString();
    }

    private void parseCallCheckString() {
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

    public String getDisplayName() {
        return "Prohibitted exception declared";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public JComponent createOptionsPanel() {
        final Form form = new Form();
        return form.getContentPanel();
    }

    public String buildErrorString(PsiElement location) {
        final PsiJavaCodeReferenceElement reference = (PsiJavaCodeReferenceElement) location;
        final PsiClass referredClass = (PsiClass) reference.resolve();

        final String exceptionName = referredClass.getName();
        return "Prohibitted exception '" + exceptionName + "' declared. #loc ";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new BadExceptionDeclaredVisitor(this, inspectionManager, onTheFly);
    }

    private class BadExceptionDeclaredVisitor extends BaseInspectionVisitor {
        private BadExceptionDeclaredVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            final PsiReferenceList throwsList = method.getThrowsList();
            if(throwsList ==null)
            {
                return;
            }
            final PsiJavaCodeReferenceElement[] references =
                    throwsList.getReferenceElements();
            if(references == null)
            {
                return;
            }
            for (int i = 0; i < references.length; i++) {
                final PsiClass thrownClass = (PsiClass) references[i].resolve();
                if(thrownClass != null)
                {
                    final String text = thrownClass.getQualifiedName();
                    for (Iterator iterator = exceptionsList.iterator(); iterator.hasNext();) {
                        final String exceptionClass = (String) iterator.next();
                        if (text.equals(exceptionClass)) {
                            registerError(references[i]);
                            continue;
                        }
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

        public JComponent getContentPanel(){
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
