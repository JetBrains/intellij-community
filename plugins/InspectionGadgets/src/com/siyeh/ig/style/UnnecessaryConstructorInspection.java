package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryConstructorInspection extends ClassInspection{
    private final UnnecessaryConstructorFix fix = new UnnecessaryConstructorFix();

    public String getID(){
        return "RedundantNoArgConstructor";
    }

    public String getDisplayName(){
        return "Redundant no-arg constructor";
    }

    public String getGroupDisplayName(){
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "No-arg constructor #ref is unnecessary #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new UnnecessaryConstructorVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class UnnecessaryConstructorFix extends InspectionGadgetsFix{
        public String getName(){
            return "Remove redundant constructor";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement nameIdentifier = descriptor.getPsiElement();
            final PsiElement constructor = nameIdentifier.getParent();
            assert constructor != null;
            deleteElement(constructor);
        }
    }

    private static class UnnecessaryConstructorVisitor
            extends BaseInspectionVisitor{
        public void visitClass(@NotNull PsiClass aClass){
            final PsiMethod[] constructors = aClass.getConstructors();
            if(constructors == null){
                return;
            }
            if(constructors.length != 1){
                return;
            }
            final PsiMethod constructor = constructors[0];
            if(!constructor.hasModifierProperty(PsiModifier.PUBLIC)){
                return;
            }
            final PsiParameterList parameterList = constructor
                    .getParameterList();
            if(parameterList == null){
                return;
            }
            if(parameterList.getParameters().length != 0){
                return;
            }
            final PsiReferenceList throwsList = constructor.getThrowsList();
            if(throwsList != null){
                final PsiJavaCodeReferenceElement[] elements = throwsList
                        .getReferenceElements();
                if(elements.length != 0){
                    return;
                }
            }
            final PsiCodeBlock body = constructor.getBody();
            if(body == null){
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            if(statements.length == 0){
                registerMethodError(constructor);
            } else if(statements.length == 1){
                final PsiStatement statement = statements[0];
                if("super();".equals(statement.getText())){
                    registerMethodError(constructor);
                }
            }
        }
    }
}
