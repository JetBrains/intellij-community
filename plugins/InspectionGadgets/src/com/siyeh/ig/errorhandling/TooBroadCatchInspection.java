package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ExceptionUtils;

import java.util.HashSet;
import java.util.Set;

public class TooBroadCatchInspection extends StatementInspection {
    public String getID(){
        return "OverlyBroadCatchBlock";
    }
    public String getDisplayName() {
        return "Overly broad 'catch' block";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(Object val) {
        return "Catch of #ref is too broad, masking exception " + val + "  #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new TooBroadCatchVisitor(this, inspectionManager, onTheFly);
    }

    private static class TooBroadCatchVisitor extends StatementInspectionVisitor {
        private TooBroadCatchVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitTryStatement(PsiTryStatement statement) {
            super.visitTryStatement(statement);

            final PsiManager manager = statement.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiCodeBlock tryBlock = statement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            final Set<PsiType> exceptionsThrown = ExceptionUtils.calculateExceptionsThrown(tryBlock, factory);
            final int numExceptionsThrown = exceptionsThrown.size();
            final Set<PsiType> exceptionsCaught = new HashSet<PsiType>(numExceptionsThrown);
            final PsiParameter[] parameters = statement.getCatchBlockParameters();
            for(final PsiParameter parameter : parameters){
                final PsiType typeCaught = parameter.getType();
                for(Object aExceptionsThrown : exceptionsThrown){
                    final PsiType typeThrown = (PsiType) aExceptionsThrown;
                    if(exceptionsCaught.contains(typeThrown)){
                        // don't do anything
                    } else if(typeCaught.equals(typeThrown)){
                        exceptionsCaught.add(typeCaught);
                    } else if(typeCaught.isAssignableFrom(typeThrown)){
                        exceptionsCaught.add(typeCaught);
                        final String typeThrownText = typeThrown.getPresentableText();
                        final PsiTypeElement typeElement = parameter.getTypeElement();
                        registerError(typeElement, typeThrownText);
                    }
                }
            }
        }
    }

}
