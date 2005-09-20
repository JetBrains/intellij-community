/*
 * Copyright 2003-2005 Dave Griffith
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public abstract class InspectionGadgetsFix implements LocalQuickFix{

    public static final Key<Object> FIX_KEY =
            Key.create("InspectionGadgetsFix.applyFix.FIX_KEY");

    //to appear in "Apply Fix" statement when multiple Quick Fixes exist
    public String getFamilyName() {
        return "";
    }

    public void applyFix(Project project,
                         ProblemDescriptor descriptor){
        final PsiElement problemElement = descriptor.getPsiElement();
        if(problemElement == null || !problemElement.isValid()){
            return;
        }
        if(isQuickFixOnReadOnlyFile(problemElement)){
            return;
        }
        final Object data = problemElement.getCopyableUserData(FIX_KEY);
        if (data == null || !data.equals(getName())) {
            return;
        }
        try{
            doFix(project, descriptor);
        } catch(IncorrectOperationException e){
            final Class<? extends InspectionGadgetsFix> aClass = getClass();
            final String className = aClass.getName();
            final Logger logger = Logger.getInstance(className);
            logger.error(e);
        }
    }

    protected abstract void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException;

    protected static void deleteElement(@NotNull PsiElement element)
            throws IncorrectOperationException{
        element.delete();
    }

    protected static void replaceExpression(PsiExpression expression,
                                            @NonNls String newExpression)
            throws IncorrectOperationException{
        final PsiManager psiManager = expression.getManager();
        final PsiElementFactory factory = psiManager.getElementFactory();
        final PsiExpression newExp =
                factory.createExpressionFromText(newExpression, expression);
        final PsiElement replacementExp = expression.replace(newExp);
        final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
        styleManager.reformat(replacementExp);
    }

    protected static void replaceExpressionAndShorten(PsiExpression expression,
                                                      String newExpression)
            throws IncorrectOperationException{
        final PsiManager psiManager = expression.getManager();
        final PsiElementFactory factory = psiManager.getElementFactory();
        final PsiExpression newExp =
                factory.createExpressionFromText(newExpression, expression);
        final PsiElement replacementExp = expression.replace(newExp);
        final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
        styleManager.shortenClassReferences(replacementExp);
        styleManager.reformat(replacementExp);
    }

    protected static void replaceStatement(PsiStatement statement,
                                           String newStatement)
            throws IncorrectOperationException{
        final PsiManager psiManager = statement.getManager();
        final PsiElementFactory factory = psiManager.getElementFactory();
        final PsiStatement newExp =
                factory.createStatementFromText(newStatement, statement);
        final PsiElement replacementExp = statement.replace(newExp);
        final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
        styleManager.reformat(replacementExp);
    }

    protected static void replaceStatementAndShortenClassNames(
            PsiStatement statement, String newStatement)
            throws IncorrectOperationException{
        final PsiManager psiManager = statement.getManager();
        final PsiElementFactory factory = psiManager.getElementFactory();
        final PsiStatement newExp =
                factory.createStatementFromText(newStatement, statement);
        final PsiElement replacementStatement = statement.replace(newExp);
        final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
        styleManager.shortenClassReferences(replacementStatement);
        styleManager.reformat(replacementStatement);
    }

    private static boolean isQuickFixOnReadOnlyFile(PsiElement problemElement){
        final PsiFile containingPsiFile = problemElement.getContainingFile();
        if(containingPsiFile == null){
            return false;
        }
        final VirtualFile virtualFile = containingPsiFile.getVirtualFile();
        final PsiManager psiManager = problemElement.getManager();
        final Project project = psiManager.getProject();
        final ReadonlyStatusHandler handler =
                ReadonlyStatusHandler.getInstance(project);
        final ReadonlyStatusHandler.OperationStatus status =
                handler.ensureFilesWritable(new VirtualFile[]{virtualFile});
        return status.hasReadonlyFiles();
    }
}