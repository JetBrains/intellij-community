package com.siyeh.ig.initialization;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeInitializerExplicitFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.InitializationUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StaticVariableInitializationInspection extends FieldInspection {
    /** @noinspection PublicField*/
    public boolean m_ignorePrimitives = false;
    private final MakeInitializerExplicitFix fix = new MakeInitializerExplicitFix();

    public String getID(){
        return "StaticVariableMayNotBeInitialized";
    }
    public String getDisplayName() {
        return "Static variable may not be initialized";
    }

    public String getGroupDisplayName() {
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Static variable #ref may not be initialized during class initialization #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore primitive fields",
                this, "m_ignorePrimitives");
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StaticVariableInitializationVisitor();
    }

    private class StaticVariableInitializationVisitor extends BaseInspectionVisitor {
        public void visitField(@NotNull PsiField field) {
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
            for(final PsiClassInitializer initializer : initializers){
                if(initializer.hasModifierProperty(PsiModifier.STATIC)){
                    final PsiCodeBlock body = initializer.getBody();
                    if(InitializationUtils.blockMustAssignVariableOrFail(field,
                                                                         body)){
                        return;
                    }
                }
            }
            registerFieldError(field);
        }

    }
}
