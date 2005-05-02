package com.siyeh.ig.initialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.MakeInitializerExplicitFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.InitializationUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class InstanceVariableInitializationInspection extends FieldInspection {
    /** @noinspection PublicField*/
    public boolean m_ignorePrimitives = false;
    private final MakeInitializerExplicitFix fix = new MakeInitializerExplicitFix();

    public String getID(){
        return "InstanceVariableMayNotBeInitialized";
    }
    public String getDisplayName() {
        return "Instance variable may not be initialized";
    }

    public String getGroupDisplayName() {
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Instance variable #ref may not be initialized during object construction #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore primitive fields",
                this, "m_ignorePrimitives");
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new InstanceVariableInitializationVisitor(this, inspectionManager, onTheFly);
    }

    private class InstanceVariableInitializationVisitor extends BaseInspectionVisitor {
        private InstanceVariableInitializationVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitField(PsiField field) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            if (field.getInitializer() != null) {
                return;
            }
            if (m_ignorePrimitives) {
                final PsiType fieldType = field.getType();
                if (ClassUtils.isPrimitive(fieldType)) {
                    return;
                }
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
            if (isInitializedInInitializer(field)) {
                return;
            }

            final PsiMethod[] constructors = aClass.getConstructors();
            if (constructors == null || constructors.length == 0) {
                registerFieldError(field);
                return;
            }

            for(final PsiMethod constructor : constructors){
                final PsiCodeBlock body = constructor.getBody();
                if(!InitializationUtils.blockMustAssignVariableOrFail(field,
                                                                      body)){
                    registerFieldError(field);
                    return;
                }
            }

        }

        private boolean isInitializedInInitializer(PsiField field) {
            final PsiClass aClass = field.getContainingClass();
            final PsiClassInitializer[] initializers = aClass.getInitializers();
            for(final PsiClassInitializer initializer : initializers){
                if(!initializer.hasModifierProperty(PsiModifier.STATIC)){
                    final PsiCodeBlock body = initializer.getBody();
                    if(InitializationUtils.blockMustAssignVariableOrFail(field,
                                                                         body)){
                        return true;
                    }
                }
            }
            return false;
        }

    }
}
