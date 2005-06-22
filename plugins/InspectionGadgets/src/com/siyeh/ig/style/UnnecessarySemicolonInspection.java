package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessarySemicolonInspection extends ClassInspection{
    private final UnnecessarySemicolonFix fix = new UnnecessarySemicolonFix();

    public String getDisplayName(){
        return "Unnecessary semicolon";
    }

    public String getGroupDisplayName(){
        return GroupNames.STYLE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return "Unnecessary semicolon #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new UnnecessarySemicolonVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class UnnecessarySemicolonFix extends InspectionGadgetsFix{
        public String getName(){
            return "Remove unnecessary semicolon";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement semicolonElement = descriptor.getPsiElement();
            deleteElement(semicolonElement);
        }
    }

    private static class UnnecessarySemicolonVisitor
                                                     extends BaseInspectionVisitor{


        public void visitClass(@NotNull PsiClass aClass){
            PsiElement sibling = skipForwardWhiteSpacesAndComments(aClass);
            while(sibling != null){
                if(sibling instanceof PsiJavaToken &&
                        ((PsiJavaToken) sibling).getTokenType()
                                .equals(JavaTokenType.SEMICOLON)){
                    registerError(sibling);
                } else{
                    break;
                }
                sibling = skipForwardWhiteSpacesAndComments(sibling);
            }

            //TODO: Dave, correct me if I'm wrong but I think that only semicolon after last member in enum is unneccessary
            //Also your indentation level differs from ours:)
            if(aClass.isEnum()){
                final PsiField[] fields = aClass.getFields();
                if(fields.length > 0){
                    final PsiField last = fields[fields.length - 1];
                    if(last instanceof PsiEnumConstant){
                        final PsiElement element = skipForwardWhiteSpacesAndComments(last);
                        if(element instanceof PsiJavaToken &&
                                ((PsiJavaToken) element).getTokenType()
                                        .equals(JavaTokenType.SEMICOLON)){
                            final PsiElement next = skipForwardWhiteSpacesAndComments(element);
                            if(next == null || next == aClass.getRBrace()){
                                registerError(element);
                            }
                        }
                    }
                }
            }
        }

        private static @Nullable  PsiElement skipForwardWhiteSpacesAndComments(PsiElement element){
            return PsiTreeUtil.skipSiblingsForward(element,
                                                   new Class[]{
                                                       PsiWhiteSpace.class,
                                                       PsiComment.class});
        }

        public void visitEmptyStatement(PsiEmptyStatement statement){
            super.visitEmptyStatement(statement);
            final PsiElement parent = statement.getParent();
            if(parent instanceof PsiCodeBlock){
                registerError(statement);
            }
        }
    }
}
