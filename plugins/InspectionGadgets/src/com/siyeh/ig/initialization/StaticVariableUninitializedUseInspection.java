package com.siyeh.ig.initialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.InitializationReadUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;
import java.util.List;

public class StaticVariableUninitializedUseInspection extends FieldInspection {
    public boolean m_ignorePrimitives = false;

    public String getID(){
        return "StaticVariableUsedBeforeInitialization";
    }
    public String getDisplayName() {
        return "Static variable used before initialization";
    }

    public String getGroupDisplayName() {
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Static variable #ref used before initialization #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore primitive fields",
                this, "m_ignorePrimitives");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StaticVariableInitializationVisitor(this, inspectionManager,
                onTheFly);
    }

    private class StaticVariableInitializationVisitor
            extends BaseInspectionVisitor {
        private StaticVariableInitializationVisitor(BaseInspection inspection,
                                                    InspectionManager inspectionManager,
                                                    boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitField(PsiField field) {
            if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            if (field.getInitializer() != null) {
                return;
            }
            final PsiClass containingClass = field.getContainingClass();

            if (containingClass == null) {
                return;
            }
            if (containingClass.isEnum()) {
                return;
            }
            if (m_ignorePrimitives) {
                final PsiType type = field.getType();
                if (ClassUtils.isPrimitive(type)) {
                    return;
                }
            }
            final PsiClassInitializer[] initializers = containingClass.getInitializers();
            // Do the static initializers come in actual order in file? (They need to.)
            final InitializationReadUtils iru;
            iru = new InitializationReadUtils();

            for (int i = 0; i < initializers.length; i++) {
                final PsiClassInitializer initializer = initializers[i];
                if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
                    final PsiCodeBlock body = initializer.getBody();
                    if (iru.blockMustAssignVariable(field, body)) {
                        break;
                    }
                }
            }

            final List badReads = iru.getUninitializedReads();
            for (int i = 0; i < badReads.size(); i++) {
                final PsiElement element = (PsiElement) badReads.get(i);
                registerError(element);
            }
        }

    }
}
