package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;

public class EnumeratedConstantNamingConventionInspection
                                                          extends ConventionInspection{
    private static final int DEFAULT_MIN_LENGTH = 5;
    private static final int DEFAULT_MAX_LENGTH = 32;
    private final RenameFix fix = new RenameFix();

    public String getDisplayName(){
        return "Enumerated constant naming convention";
    }

    public String getGroupDisplayName(){
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        final PsiField field = (PsiField) location.getParent();
        assert field != null;
        final String fieldName = field.getName();
        if(fieldName.length() < getMinLength()){
            return "Enumerated constant name '#ref' is too short #loc";
        } else if(fieldName.length() > getMaxLength()){
            return "Enumerated constant name '#ref' is too long #loc";
        }
        return "Enumerated constant '#ref' doesn't match regex '" + getRegex() +
                "' #loc";
    }

    protected String getDefaultRegex(){
        return "[A-Z][A-Za-z]*";
    }

    protected int getDefaultMinLength(){
        return DEFAULT_MIN_LENGTH;
    }

    protected int getDefaultMaxLength(){
        return DEFAULT_MAX_LENGTH;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NamingConventionsVisitor();
    }

    public ProblemDescriptor[] doCheckField(PsiField field,
                                            InspectionManager manager,
                                            boolean isOnTheFly){
        final PsiClass containingClass = field.getContainingClass();
        if(containingClass == null){
            return super.doCheckField(field, manager, isOnTheFly);
        }
        if(!containingClass.isPhysical()){
            return super.doCheckField(field, manager, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(manager,
                                                            isOnTheFly);
        field.accept(visitor);
        return visitor.getErrors();
    }

    private class NamingConventionsVisitor extends BaseInspectionVisitor{
        public void visitEnumConstant(PsiEnumConstant constant){
            super.visitEnumConstant(constant);
            final String name = constant.getName();
            if(name == null){
                return;
            }
            if(isValid(name)){
                return;
            }
            registerFieldError(constant);
        }
    }
}
