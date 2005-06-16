package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.VariableInspection;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

public class MismatchedArrayReadWriteInspection extends VariableInspection{
    public String getID(){
        return "MismatchedReadAndWriteOfArray";
    }

    public String getDisplayName(){
        return "Mismatched read and write of array";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        final PsiVariable variable = (PsiVariable) location.getParent();
        assert variable!=null;
        final PsiElement context;
        if(variable instanceof PsiField){
            context = ((PsiMember) variable).getContainingClass();
        } else{
            context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        }
        final boolean written = arrayContentsAreWritten(variable, context);
        if(written){
            return "Contents of array #ref are written to, but never read #loc";
        } else{
            return "Contents of array #ref are read, but never written to #loc";
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MismatchedArrayReadWriteVisitor();
    }

    private static class MismatchedArrayReadWriteVisitor
                                                         extends BaseInspectionVisitor{

        public void visitField(@NotNull PsiField field){
            super.visitField(field);
            if(!field.hasModifierProperty(PsiModifier.PRIVATE)){
                return;
            }
            final PsiClass containingClass = field.getContainingClass();
            if(containingClass == null){
                return;
            }
            final PsiType type = field.getType();
            if(type.getArrayDimensions() == 0){
                return;
            }
            final boolean written = arrayContentsAreWritten(field,
                                                            containingClass);
            final boolean read = arrayContentsAreRead(field, containingClass);
            if(written != read){
                registerFieldError(field);
            }
        }

        public void visitLocalVariable(@NotNull PsiLocalVariable variable){
            super.visitLocalVariable(variable);
            final PsiCodeBlock codeBlock =
                    PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if(codeBlock == null){
                return;
            }
            final PsiType type = variable.getType();
            if(type.getArrayDimensions() == 0){
                return;
            }
            final boolean written = arrayContentsAreWritten(variable,
                                                            codeBlock);
            final boolean read = arrayContentsAreRead(variable, codeBlock);
            if(written && read){
                return;
            }
            if(!read && !written){
                return;
            }
            registerVariableError(variable);
        }
    }

    private static boolean arrayContentsAreWritten(PsiVariable variable,
                                                   PsiElement context){
        if(VariableAccessUtils.arrayContentsAreAssigned(variable, context)){
            return true;
        }
        final PsiExpression initializer = variable.getInitializer();
        if(initializer != null && !isDefaultArrayInitializer(initializer)){
            return true;
        }
        if(VariableAccessUtils.variableIsAssigned(variable, context)){
            return true;
        }
        if(VariableAccessUtils.variableIsAssignedFrom(variable, context)){
            return true;
        }
        if(VariableAccessUtils.variableIsReturned(variable, context)){
            return true;
        }
        return VariableAccessUtils.variableIsPassedAsMethodArgument(variable,
                                                                    context);
    }

    private static boolean isDefaultArrayInitializer(PsiExpression initializer){
        if(!(initializer instanceof PsiNewExpression)){
            return false;
        }
        final PsiNewExpression newExpression = (PsiNewExpression) initializer;
        return newExpression.getArrayInitializer() == null;
    }

    private static boolean arrayContentsAreRead(PsiVariable variable,
                                                PsiElement context){
        if(VariableAccessUtils.arrayContentsAreAccessed(variable, context)){
            return true;
        }
        final PsiExpression initializer = variable.getInitializer();
        if(initializer != null && !isDefaultArrayInitializer(initializer)){
            return true;
        }
        if(VariableAccessUtils.variableIsAssigned(variable, context)){
            return true;
        }
        if(VariableAccessUtils.variableIsAssignedFrom(variable, context)){
            return true;
        }
        if(VariableAccessUtils.variableIsReturned(variable, context)){
            return true;
        }
        return VariableAccessUtils.variableIsPassedAsMethodArgument(variable,
                                                                    context);
    }
}

