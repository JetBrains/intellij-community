/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public class MakeClosureCallImplicitIntention extends Intention {


    @Override
    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ExplicitClosureCallPredicate();
    }

    @Override
    public void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor)
            throws IncorrectOperationException {
        final GrMethodCallExpression expression =
                (GrMethodCallExpression) element;
        final GrReferenceExpression invokedExpression = (GrReferenceExpression) expression.getInvokedExpression();
        final GrExpression qualifier = invokedExpression.getQualifierExpression();
        final GrArgumentList argList = expression.getArgumentList();
        final GrClosableBlock[] closureArgs = expression.getClosureArguments();
        final StringBuilder newExpression = new StringBuilder();
        newExpression.append(qualifier.getText());
        newExpression.append(argList.getText());
        for (GrClosableBlock closureArg : closureArgs) {
            newExpression.append(closureArg.getText());
        }
        PsiImplUtil.replaceExpression(newExpression.toString(), expression);
    }
}
