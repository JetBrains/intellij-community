package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.psiutils.ExceptionUtils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class TooBroadCatchInspection extends StatementInspection {

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

    private static class TooBroadCatchVisitor extends BaseInspectionVisitor {
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
            final Set exceptionsThrown = ExceptionUtils.calculateExceptionsThrown(tryBlock, factory);
            final int numExceptionsThrown = exceptionsThrown.size();
            final Set exceptionsCaught = new HashSet(numExceptionsThrown);
            final PsiParameter[] parameters = statement.getCatchBlockParameters();
            for (int i = 0; i < parameters.length; i++) {
                final PsiParameter parameter = parameters[i];
                final PsiType typeCaught = parameter.getType();
                for (Iterator iterator = exceptionsThrown.iterator(); iterator.hasNext();) {
                    final PsiType typeThrown = (PsiType) iterator.next();
                    if (exceptionsCaught.contains(typeThrown)) {
                        // don't do anything
                    } else if (typeCaught.equals(typeThrown)) {
                        exceptionsCaught.add(typeCaught);
                    } else if (typeCaught.isAssignableFrom(typeThrown)) {
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
