package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;
import java.util.Set;

public class FieldRepeatedlyAccessedInspection extends MethodInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreFinalFields = false;

    public String getID(){
        return "FieldRepeatedlyAccessedInMethod";
    }

    public String getDisplayName() {
        return "Field repeatedly accessed in method";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(Object arg) {
        final String fieldName = ((PsiNamedElement) arg).getName();
        return "Field " + fieldName + " accessed repeatedly in method #ref #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore final fields",
                this, "m_ignoreFinalFields");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new FieldRepeatedlyAccessedVisitor(this, inspectionManager, onTheFly);
    }

    private class FieldRepeatedlyAccessedVisitor extends BaseInspectionVisitor {
        private FieldRepeatedlyAccessedVisitor(BaseInspection inspection,
                                                          InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            final PsiIdentifier nameIdentifier = method.getNameIdentifier();
            if (nameIdentifier == null) {
                return;
            }
            final VariableAccessVisitor visitor = new VariableAccessVisitor();
            method.accept(visitor);
            final Set<PsiElement> fields = visitor.getOveraccessedFields();
            for(Object field1 : fields){
                final PsiField field = (PsiField) field1;
                if(!m_ignoreFinalFields ||
                        !field.hasModifierProperty(PsiModifier.FINAL)){
                    registerError(nameIdentifier, field);
                }
            }
        }

    }

}
