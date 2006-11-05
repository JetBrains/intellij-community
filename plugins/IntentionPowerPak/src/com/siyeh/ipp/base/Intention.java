/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
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
        super();
        predicate = getElementPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if(isFileReadOnly(project, file)){
            return;
        }
        final PsiElement element = findMatchingElement(file, editor);
        if(element == null){
            return;
        }
        processIntention(element);
    }

    protected abstract void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException;

    @NotNull protected abstract PsiElementPredicate getElementPredicate();

    protected static void replaceExpression(@NotNull String newExpression,
                                            @NotNull PsiExpression expression)
            throws IncorrectOperationException{
        final PsiManager mgr = expression.getManager();
        final PsiElementFactory factory = mgr.getElementFactory();
        final PsiExpression newCall =
                factory.createExpressionFromText(newExpression, null);
        final PsiElement insertedElement = expression.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceExpressionWithNegatedExpression(
            @NotNull PsiExpression newExpression,
            @NotNull PsiExpression expression)
            throws IncorrectOperationException{
        final PsiManager manager = expression.getManager();
        final PsiElementFactory factory = manager.getElementFactory();

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
                factory.createExpressionFromText(expString, null);
        assert expressionToReplace != null;
        final PsiElement insertedElement = expressionToReplace.replace(newCall);
        final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceExpressionWithNegatedExpressionString(
            @NotNull String newExpression,
            @NotNull PsiExpression expression)
            throws IncorrectOperationException{
        final PsiManager mgr = expression.getManager();
        final PsiElementFactory factory = mgr.getElementFactory();

        PsiExpression expressionToReplace = expression;
        final String expString;
        if(BoolUtils.isNegated(expression)){
            expressionToReplace = BoolUtils.findNegation(expression);
            expString = newExpression;
        } else{
            expString = "!(" + newExpression + ')';
        }
        final PsiExpression newCall =
                factory.createExpressionFromText(expString, null);
        assert expressionToReplace != null;
        final PsiElement insertedElement = expressionToReplace.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceStatement(
            @NonNls @NotNull String newStatement,
            @NonNls @NotNull PsiStatement statement)
            throws IncorrectOperationException{
        final PsiManager mgr = statement.getManager();
        final PsiElementFactory factory = mgr.getElementFactory();
        final PsiStatement newCall =
                factory.createStatementFromText(newStatement, statement);
        final PsiElement insertedElement = statement.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceStatementAndShorten(
            @NonNls @NotNull String newStatement,
            @NonNls @NotNull PsiStatement statement)
            throws IncorrectOperationException{
        final PsiManager mgr = statement.getManager();
        final PsiElementFactory factory = mgr.getElementFactory();
        final PsiStatement newCall =
                factory.createStatementFromText(newStatement, statement);
        final PsiElement insertedElement = statement.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        final PsiElement shortenedElement =
                codeStyleManager.shortenClassReferences(insertedElement);
        codeStyleManager.reformat(shortenedElement);
    }

    @Nullable PsiElement findMatchingElement(PsiFile file,
                                             Editor editor){
        final CaretModel caretModel = editor.getCaretModel();
        final int position = caretModel.getOffset();
        PsiElement element = file.findElementAt(position);
        while(element != null){
            if(predicate.satisfiedBy(element)){
                return element;
            } else{
                element = element.getParent();
                if (element instanceof PsiFile) {
                    break;
                }
            }
        }
        return null;
    }


    public boolean isAvailable(Project project, Editor editor, PsiElement element) {
      while (element != null) {
        if (predicate.satisfiedBy(element)) {
          return true;
        }
        else {
          element = element.getParent();
          if (element instanceof PsiFile) {
            break;
          }
        }
      }
      return false;
    }

    public boolean startInWriteAction(){
        return true;
    }

    private static boolean isFileReadOnly(Project project, PsiFile file){
        final VirtualFile virtualFile = file.getVirtualFile();
        final ReadonlyStatusHandler readonlyStatusHandler =
                ReadonlyStatusHandler.getInstance(project);
        final ReadonlyStatusHandler.OperationStatus operationStatus =
                readonlyStatusHandler.ensureFilesWritable(virtualFile);
        return operationStatus.hasReadonlyFiles();
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

    @NotNull
    public String getText() {
        //noinspection UnresolvedPropertyKey
        return IntentionPowerPackBundle.message(getPrefix() + ".name");
    }

    @NotNull
    public String getFamilyName() {
        //noinspection UnresolvedPropertyKey
        return IntentionPowerPackBundle.message(getPrefix() + ".family.name");
    }
}