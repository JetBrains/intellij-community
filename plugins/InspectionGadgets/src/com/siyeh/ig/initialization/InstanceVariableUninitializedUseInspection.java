package com.siyeh.ig.initialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.InitializationReadUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;
import java.util.List;

public class InstanceVariableUninitializedUseInspection
        extends FieldInspection {
    public boolean m_ignorePrimitives = false;

    public String getDisplayName() {
        return "Instance variable used before initialized";
    }

    public String getGroupDisplayName() {
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Instance variable #ref used before initialized #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore primitive fields",
                this, "m_ignorePrimitives");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new InstanceVariableInitializationVisitor(this,
                inspectionManager,
                onTheFly);
    }

    private class InstanceVariableInitializationVisitor
            extends BaseInspectionVisitor {
        private InstanceVariableInitializationVisitor(BaseInspection inspection, InspectionManager inspectionManager,
                                                      boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        private InitializationReadUtils iru = new InitializationReadUtils();

        public void visitField(PsiField field) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            if (field.getInitializer() != null) {
                return;
            }
            if (m_ignorePrimitives &&
                    ClassUtils.isPrimitive(field.getType())) {
                return;
            }
            final PsiClass aClass = field.getContainingClass();
            if (aClass == null) {
                return;
            }
            final PsiManager manager = field.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            if (searchHelper.isFieldBoundToForm(field)) {
                return;
            }

            if (!isInitializedInInitializer(field)) {
                final PsiMethod[] constructors = aClass.getConstructors();

                for (int i = 0; i < constructors.length; i++) {
                    final PsiMethod constructor = constructors[i];
                    final PsiCodeBlock body = constructor.getBody();
                    iru.blockMustAssignVariable(field, body);
                }
            }

            final List badReads = iru.getUninitializedReads();
            for (int i = 0; i < badReads.size(); i++) {
                final PsiElement element = (PsiElement) badReads.get(i);
                registerError(element);
            }

        }

        private boolean isInitializedInInitializer(PsiField field) {
            final PsiClass aClass = field.getContainingClass();
            final PsiClassInitializer[] initializers = aClass.getInitializers();
            for (int i = 0; i < initializers.length; i++) {
                final PsiClassInitializer initializer = initializers[i];
                if (!initializer.hasModifierProperty(PsiModifier.STATIC)) {
                    final PsiCodeBlock body = initializer.getBody();
                    if (iru.blockMustAssignVariable(field, body)) {
                        return true;
                    }
                }
            }
            return false;
        }

    }
}
