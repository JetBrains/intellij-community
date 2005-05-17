package com.siyeh.ig.j2me;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.performance.VariableAccessVisitor;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

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
        return GroupNames.J2ME_GROUP_NAME;
    }

    public String buildErrorString(Object arg) {
        final String fieldName = ((PsiNamedElement) arg).getName();
        return "Field " + fieldName + " accessed repeatedly in method #ref #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore final fields",
                this, "m_ignoreFinalFields");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new FieldRepeatedlyAccessedVisitor();
    }

    private class FieldRepeatedlyAccessedVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
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
