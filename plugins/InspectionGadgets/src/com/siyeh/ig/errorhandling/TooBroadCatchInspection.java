package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ExceptionUtils;

import java.util.*;

public class TooBroadCatchInspection extends StatementInspection{
    public String getID(){
        return "OverlyBroadCatchBlock";
    }

    public String getDisplayName(){
        return "Overly broad 'catch' block";
    }

    public String getGroupDisplayName(){
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    protected String buildErrorString(PsiElement location){
        final PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(location,
                                                                         PsiTryStatement.class);
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        final PsiElementFactory factory = tryStatement.getManager()
                .getElementFactory();
        final Set<PsiType> exceptionsThrown = ExceptionUtils.calculateExceptionsThrown(tryBlock,
                                                                                       factory);
        final int numExceptionsThrown = exceptionsThrown.size();
        final Set<PsiType> exceptionsCaught = new HashSet<PsiType>(numExceptionsThrown);

        final PsiParameter[] parameters = tryStatement.getCatchBlockParameters();
        final List<String> typesMasked = new ArrayList<String>();
        for(final PsiParameter parameter : parameters){
            if(parameter.equals(location.getParent())){
                final PsiType typeCaught = parameter.getType();
                for(Object aExceptionsThrown : exceptionsThrown){
                    final PsiType typeThrown = (PsiType) aExceptionsThrown;
                    if(exceptionsCaught.contains(typeThrown)){
                        // don't do anything
                    } else if(typeCaught.equals(typeThrown)){
                        exceptionsCaught.add(typeCaught);
                    } else if(typeCaught.isAssignableFrom(typeThrown)){
                        exceptionsCaught.add(typeCaught);
                        typesMasked.add(typeThrown.getPresentableText());
                    }
                }
            }
        }
        Collections.sort(typesMasked);
        String typesMaskedString = "";
        for(int i = 0; i < typesMasked.size(); i++){
            if(i == typesMasked.size() - 1){
                typesMaskedString += " and ";
            } else if(i != 0){
                typesMaskedString += ", ";
            }
            typesMaskedString += typesMasked.get(i);
        }
        if(typesMasked.size() == 1)
        {
            return "Catch of #ref is too broad, masking exception " +
                typesMaskedString + "  #loc";
        }
        else
        {
            return "Catch of #ref is too broad, masking exceptions " +
                    typesMaskedString + "  #loc";
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new TooBroadCatchVisitor(this, inspectionManager, onTheFly);
    }

    private static class TooBroadCatchVisitor
            extends StatementInspectionVisitor{
        private TooBroadCatchVisitor(BaseInspection inspection,
                                     InspectionManager inspectionManager,
                                     boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitTryStatement(PsiTryStatement statement){
            super.visitTryStatement(statement);

            final PsiManager manager = statement.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiCodeBlock tryBlock = statement.getTryBlock();
            if(tryBlock == null){
                return;
            }
            final Set<PsiType> exceptionsThrown = ExceptionUtils.calculateExceptionsThrown(tryBlock,
                                                                                           factory);
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
                        final PsiTypeElement typeElement = parameter.getTypeElement();
                        registerError(typeElement);
                        return;
                    }
                }
            }
        }
    }
}
