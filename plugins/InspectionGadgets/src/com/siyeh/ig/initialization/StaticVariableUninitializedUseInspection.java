package com.siyeh.ig.initialization;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.InitializationReadUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class StaticVariableUninitializedUseInspection extends FieldInspection {
    /** @noinspection PublicField*/
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

    public BaseInspectionVisitor buildVisitor() {
        return new StaticVariableInitializationVisitor();
    }

    private class StaticVariableInitializationVisitor
            extends BaseInspectionVisitor {

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
            // Do the static initializers come in actual order in file? (They need to.)
            final InitializationReadUtils iru = new InitializationReadUtils();

            for(final PsiClassInitializer initializer : initializers){
                if(initializer.hasModifierProperty(PsiModifier.STATIC)){
                    final PsiCodeBlock body = initializer.getBody();
                    if(iru.blockMustAssignVariable(field, body)){
                        break;
                    }
                }
            }

            final List<PsiExpression> badReads = iru.getUninitializedReads();
            for(PsiExpression badRead : badReads){
                registerError(badRead);
            }
        }

    }
}
