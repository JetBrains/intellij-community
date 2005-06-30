package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IgnoreResultOfCallInspection extends ExpressionInspection{
    /** @noinspection PublicField*/
    public boolean m_reportAllNonLibraryCalls = false;

    /** @noinspection PublicField*/
    public String callCheckString = "java.io.InputStream,read," +
        "java.io.InputStream,skip," +
        "java.lang.StringBuffer,toString," +
        "java.lang.StringBuilder,toString," +
        "java.lang.String,.*," +
        "java.math.BigInteger,.*," +
        "java.math.BigDecimal,.*," +
        "java.net.InetAddress,.*";

    private final List<ReturnCheckSpecification> callsToCheck = new ArrayList<ReturnCheckSpecification>(32);

    {
        parseCallCheckString();
    }

    public void readSettings(Element element) throws InvalidDataException{
        super.readSettings(element);
        parseCallCheckString();
    }

    private void parseCallCheckString(){
        callsToCheck.clear();
        final String[] strings = callCheckString.split(",");
        for(int i = 0; i < strings.length; i += 2){
            final String className = strings[i];
            final String methodName = strings[i + 1];
            callsToCheck.add(
                    new ReturnCheckSpecification(className, methodName));
        }
    }

    public void writeSettings(Element element) throws WriteExternalException{
        formatCallCheckString();
        super.writeSettings(element);
    }

    private void formatCallCheckString(){
        final StringBuffer buffer = new StringBuffer();
        boolean first = true;
        for(Object aCallsToCheck : callsToCheck){
            if(first){
                first = false;
            } else{
                buffer.append(',');
            }
            final ReturnCheckSpecification returnCheckSpecification =
                    (ReturnCheckSpecification) aCallsToCheck;
            final String methodName = returnCheckSpecification.getMethodName();
            final String className = returnCheckSpecification.getClassName();
            buffer.append(className);
            buffer.append(',');
            buffer.append(methodName);
        }
        callCheckString = buffer.toString();
    }

    public String getID(){
        return "ResultOfMethodCallIgnored";
    }

    public String getDisplayName(){
        return "Result of method call ignored";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public JComponent createOptionsPanel(){
        final Form form = new Form();
        return form.getContentPanel();
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        final PsiElement parent = location.getParent();
        assert parent != null;
        final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) parent.getParent();
        assert methodCallExpression != null;
        final PsiMethod method = methodCallExpression.resolveMethod();
        assert method != null;
        final PsiClass containingClass = method.getContainingClass();
        assert containingClass != null;
        final String className = containingClass.getName();
        return "result of " + className + ".#ref() is ignored. #loc ";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new IgnoreResultOfCallVisitor();
    }

    private class IgnoreResultOfCallVisitor extends BaseInspectionVisitor{

        public void visitExpressionStatement(@NotNull PsiExpressionStatement statement){
            super.visitExpressionStatement(statement);
            if(!(statement.getExpression() instanceof PsiMethodCallExpression)){
                return;
            }
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression) statement.getExpression();
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
                registerMethodCallError(call);
                return;
            }
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if(methodName == null){
                return;
            }
            for(Object aCallsToCheck : callsToCheck){
                final ReturnCheckSpecification spec =
                        (ReturnCheckSpecification) aCallsToCheck;
                final Pattern methodNamePattern = spec.getMethodNamePattern();
                if(methodNamePattern != null &&
                        methodNamesMatch(methodName, methodNamePattern)){
                    final String classNameToCompare = spec.getClassName();
                    if(ClassUtils.isSubclass(aClass, classNameToCompare)){
                        registerMethodCallError(call);
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

    /** @noinspection PublicInnerClass*/
    public class Form{
        private JPanel contentPanel;
        private JButton addButton;
        private JButton deleteButton;
        private JTable table;
        private JCheckBox nonLibraryCheckbox;

        public Form(){
            super();
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            table.setRowSelectionAllowed(true);
            table.setSelectionMode(
                    ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            table.setEnabled(true);
            final ReturnCheckSpecificationTableModel model =
                    new ReturnCheckSpecificationTableModel();
            table.setModel(model);
            addButton.setEnabled(true);
            addButton.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e){
                    callsToCheck.add(new ReturnCheckSpecification());
                    model.fireTableStructureChanged();
                }
            });
            deleteButton.setEnabled(true);
            deleteButton.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e){
                    final int[] selectedRows = table.getSelectedRows();
                    Arrays.sort(selectedRows);
                    for(int i = selectedRows.length - 1; i >= 0; i--){
                        callsToCheck.remove(selectedRows[i]);
                    }
                    model.fireTableStructureChanged();
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
            return callsToCheck.size();
        }

        public int getColumnCount(){
            return 2;
        }

        public String getColumnName(int columnIndex){
            if(columnIndex == 0){
                return "Class name";
            }
            return "Method name";
        }

        public Class getColumnClass(int columnIndex){
            return String.class;
        }

        public boolean isCellEditable(int rowIndex, int columnIndex){
            return true;
        }

        public Object getValueAt(int rowIndex, int columnIndex){
            final ReturnCheckSpecification spec =
                    callsToCheck.get(
                            rowIndex);
            if(columnIndex == 0){
                return spec.getClassName();
            } else{
                return spec.getMethodName();
            }
        }

        public void setValueAt(Object aValue, int rowIndex, int columnIndex){
            final ReturnCheckSpecification spec =
                    callsToCheck.get(
                            rowIndex);
            if(columnIndex == 0){
                spec.setClassName((String) aValue);
            } else{
                spec.setMethodName((String) aValue);
            }
        }
    }
}
