package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NonBooleanMethodNameMayNotStartWithQuestionInspection extends MethodInspection{
    /** @noinspection PublicField*/
    public String nameCheckString = "is,can,has,should";
    private final RenameFix fix = new RenameFix();

    private List<Object> nameList = new ArrayList<Object>(32);

    {
        parseNameString();
    }

    public void readSettings(Element element) throws InvalidDataException{
        super.readSettings(element);
        parseNameString();
    }

    private void parseNameString(){
        nameList.clear();
        final String[] strings = nameCheckString.split(",");
        for(String string : strings){
            nameList.add(string);
        }
    }

    public void writeSettings(Element element) throws WriteExternalException{
        formatNameCheckString();
        super.writeSettings(element);
    }

    private void formatNameCheckString(){
        final StringBuffer buffer = new StringBuffer();
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
        nameCheckString = buffer.toString();
    }

    public String getDisplayName(){
        return "Non-boolean method name must not start with question";
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

    public String buildErrorString(PsiElement location){
        return "Non-boolean method name '#ref' start with a question word #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new QuestionableNameVisitor(this, inspectionManager, onTheFly);
    }

    private class QuestionableNameVisitor extends BaseInspectionVisitor{
        private boolean inClass = false;


        private QuestionableNameVisitor(BaseInspection inspection,
                                        InspectionManager inspectionManager,
                                        boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method){
            super.visitMethod(method);
            final PsiType returnType = method.getReturnType();
            if(returnType== null)
            {
                return;
            }
            if(returnType.equals(PsiType.BOOLEAN))
            {
                return;
            }
            final String name = method.getName();
            boolean startsWithQuestionWord = false;
            for(Object aNameList : nameList){
                final String prefix = (String) aNameList;
                if(name.startsWith(prefix)){
                    final char nextChar = name.charAt(prefix.length());
                    if(Character.isUpperCase(nextChar) || nextChar == '_'){
                        startsWithQuestionWord = true;
                        break;
                    }
                }
            }
            if(!startsWithQuestionWord)
            {
                return;
            }
            if(isOverrideOfLibraryMethod(method)){
                return;
            }
            registerMethodError(method);

        }

        public void visitClass(PsiClass aClass){
            if(inClass){
                return;
            }
            final boolean wasInClass = inClass;
            inClass = true;
            super.visitClass(aClass);
            inClass = wasInClass;
        }

        private boolean isOverrideOfLibraryMethod(PsiMethod method){
            final PsiMethod[] superMethods =
                    PsiSuperMethodUtil.findSuperMethods(method);

            for(PsiMethod superMethod : superMethods){
                final PsiClass containingClass =
                        superMethod.getContainingClass();
                if(containingClass != null &&
                        LibraryUtil.classIsInLibrary(containingClass)){
                    return true;
                }
            }
            return false;
        }
    }

    public class Form{
        private JPanel contentPanel;
        private JButton addButton;
        private JButton deleteButton;
        private JTable table;

        public Form(){
            super();
            table.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            table.setRowSelectionAllowed(true);
            table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            table.setEnabled(true);
            final NameListTableModel model =
            new NameListTableModel();
            table.setModel(model);
            addButton.setEnabled(true);
            addButton.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e){
                    nameList.add("");
                    model.fireTableStructureChanged();
                }
            });
            deleteButton.setEnabled(true);
            deleteButton.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e){
                    final int[] selectedRows = table.getSelectedRows();
                    Arrays.sort(selectedRows);
                    for(int i = selectedRows.length - 1; i >= 0; i--){
                        nameList.remove(selectedRows[i]);
                    }
                    model.fireTableStructureChanged();
                }
            });
        }

        public JComponent getContentPanel(){
            return contentPanel;
        }
    }

    private class NameListTableModel extends AbstractTableModel{
        public int getRowCount(){
            return nameList.size();
        }

        public int getColumnCount(){
            return 1;
        }

        public String getColumnName(int columnIndex){
            return "Boolean method name prefix";
        }

        public Class getColumnClass(int columnIndex){
            return String.class;
        }

        public boolean isCellEditable(int rowIndex, int columnIndex){
            return true;
        }

        public Object getValueAt(int rowIndex, int columnIndex){
            return nameList.get(rowIndex);
        }

        public void setValueAt(Object aValue, int rowIndex, int columnIndex){
            nameList.set(rowIndex, aValue);
        }
    }
}
