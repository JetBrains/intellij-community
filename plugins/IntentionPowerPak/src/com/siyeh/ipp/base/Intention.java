/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.base;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.psiutils.BoolUtils;
import com.siyeh.ipp.psiutils.ComparisonUtils;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Intention extends PsiElementBaseIntentionAction {

    private final PsiElementPredicate predicate;

    /** @noinspection AbstractMethodCallInConstructor,OverridableMethodCallInConstructor*/
    protected Intention(){
        predicate = getElementPredicate();
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement element)
            throws IncorrectOperationException {
        if (!isWritable(project, element)) {
            return;
        }
        final PsiElement matchingElement = findMatchingElement(element);
        if (matchingElement == null) {
            return;
        }
        processIntention(matchingElement);
    }

    protected abstract void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException;

    @NotNull protected abstract PsiElementPredicate getElementPredicate();

    protected static void replaceExpression(@NotNull String newExpression,
                                            @NotNull PsiExpression expression)
            throws IncorrectOperationException{
        final Project project = expression.getProject();
        final PsiElementFactory factory =
                JavaPsiFacade.getElementFactory(project);
        final PsiExpression newCall =
                factory.createExpressionFromText(newExpression, expression);
        final PsiElement insertedElement = expression.replace(newCall);
        final CodeStyleManager codeStyleManager =
                CodeStyleManager.getInstance(project);
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceExpressionWithNegatedExpression(
            @NotNull PsiExpression newExpression,
            @NotNull PsiExpression expression)
            throws IncorrectOperationException{
        final Project project = expression.getProject();
        final PsiElementFactory factory =
                JavaPsiFacade.getElementFactory(project);
        PsiExpression expressionToReplace = expression;
        final String newExpressionText = newExpression.getText();
        final String expString;
        if(BoolUtils.isNegated(expression)){
            expressionToReplace = BoolUtils.findNegation(expression);
            expString = newExpressionText;
        } else if(ComparisonUtils.isComparison(newExpression)){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) newExpression;
            final PsiJavaToken sign = binaryExpression.getOperationSign();
            final String negatedComparison =
                    ComparisonUtils.getNegatedComparison(sign);
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            assert rhs != null;
            expString = lhs.getText() + negatedComparison + rhs.getText();
        } else{
            if(ParenthesesUtils.getPrecedence(newExpression) >
                    ParenthesesUtils.PREFIX_PRECEDENCE){
                expString = "!(" + newExpressionText + ')';
            } else{
                expString = '!' + newExpressionText;
            }
        }
        final PsiExpression newCall =
                factory.createExpressionFromText(expString, expression);
        assert expressionToReplace != null;
        final PsiElement insertedElement = expressionToReplace.replace(newCall);
        final CodeStyleManager codeStyleManager =
                CodeStyleManager.getInstance(project);
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceExpressionWithNegatedExpressionString(
            @NotNull String newExpression,
            @NotNull PsiExpression expression)
            throws IncorrectOperationException{
        final Project project = expression.getProject();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiElementFactory factory = psiFacade.getElementFactory();
        PsiExpression expressionToReplace = expression;
        final String expString;
        if(BoolUtils.isNegated(expression)){
            expressionToReplace = BoolUtils.findNegation(expression);
            expString = newExpression;
        } else{
            expString = "!(" + newExpression + ')';
        }
        final PsiExpression newCall =
                factory.createExpressionFromText(expString, expression);
        assert expressionToReplace != null;
        final PsiElement insertedElement = expressionToReplace.replace(newCall);
        final CodeStyleManager codeStyleManager =
                CodeStyleManager.getInstance(project);
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceStatement(
            @NonNls @NotNull String newStatementText,
            @NonNls @NotNull PsiStatement statement)
            throws IncorrectOperationException{
        final Project project = statement.getProject();
        final PsiElementFactory factory =
                JavaPsiFacade.getElementFactory(project);
        final PsiStatement newStatement =
                factory.createStatementFromText(newStatementText, statement);
        final PsiElement insertedElement = statement.replace(newStatement);
        final CodeStyleManager codeStyleManager =
                CodeStyleManager.getInstance(project);
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceStatementAndShorten(
            @NonNls @NotNull String newStatementText,
            @NonNls @NotNull PsiStatement statement)
            throws IncorrectOperationException{
        final Project project = statement.getProject();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiElementFactory factory = psiFacade.getElementFactory();
        final PsiStatement newStatement =
                factory.createStatementFromText(newStatementText, statement);
        final PsiElement insertedElement = statement.replace(newStatement);
        final JavaCodeStyleManager javaCodeStyleManager =
                JavaCodeStyleManager.getInstance(project);
        final PsiElement shortenedElement =
                javaCodeStyleManager.shortenClassReferences(insertedElement);
        final CodeStyleManager codeStyleManager =
                CodeStyleManager.getInstance(project);
        codeStyleManager.reformat(shortenedElement);
    }

    @Nullable PsiElement findMatchingElement(@Nullable PsiElement element) {
        while(element != null){
            if(!StdLanguages.JAVA.equals(element.getLanguage())){
                break;
            }
            if(predicate.satisfiedBy(element)){
                return element;
            }
            element = element.getParent();
            if (element instanceof PsiFile) {
                break;
            }
        }
        return null;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor,
                               @NotNull PsiElement element) {
        return findMatchingElement(element) != null;
    }

    @Override
    public boolean startInWriteAction(){
        return true;
    }

    private static boolean isWritable(Project project, PsiElement element){
        final VirtualFile virtualFile = PsiUtil.getVirtualFile(element);
        if (virtualFile == null) {
            return true;
        }
        final ReadonlyStatusHandler readonlyStatusHandler =
                ReadonlyStatusHandler.getInstance(project);
        final ReadonlyStatusHandler.OperationStatus operationStatus =
                readonlyStatusHandler.ensureFilesWritable(virtualFile);
        return !operationStatus.hasReadonlyFiles();
    }

    private String getPrefix() {
        final Class<? extends Intention> aClass = getClass();
        final String name = aClass.getSimpleName();
        final StringBuilder buffer = new StringBuilder(name.length() + 10);
        buffer.append(Character.toLowerCase(name.charAt(0)));
        for (int i = 1; i < name.length(); i++){
            final char c = name.charAt(i);
            if (Character.isUpperCase(c)){
                buffer.append('.');
                buffer.append(Character.toLowerCase(c));
            } else {
                buffer.append(c);
            }
        }
        return buffer.toString();
    }

    @Override
    @NotNull
    public String getText() {
        //noinspection UnresolvedPropertyKey
        return IntentionPowerPackBundle.message(getPrefix() + ".name");
    }

    @NotNull
    public String getFamilyName() {
        //noinspection UnresolvedPropertyKey
        return IntentionPowerPackBundle.defaultableMessage(getPrefix() + ".family.name");
    }
}