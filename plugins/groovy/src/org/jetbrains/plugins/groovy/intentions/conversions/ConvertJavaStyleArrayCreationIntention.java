/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

/**
 * @author Maxim.Medvedev
 */
public class ConvertJavaStyleArrayCreationIntention extends Intention {
  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final GrClosableBlock block = ((GrMethodCallExpression)element).getClosureArguments()[0];
    final String text = block.getText();
    int start = block.getLBrace().getStartOffsetInParent() + 1;
    int finish = block.getRBrace().getStartOffsetInParent();
    String newText = "[" + text.substring(start, finish) + "]";
    final GrExpression newExpr = GroovyPsiElementFactory.getInstance(element.getProject()).createExpressionFromText(newText);
    ((GrMethodCallExpression)element).replaceWithStatement(newExpr);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof GrMethodCallExpression)) return false;
        final GrExpression expression = ((GrMethodCallExpression)element).getInvokedExpression();
        if (!(expression instanceof GrNewExpression)) return false;
        if (((GrNewExpression)expression).getArrayCount() == 0) return false;

        if (((GrMethodCallExpression)element).getArgumentList().getText().trim().length() > 0) return false;

        final GrClosableBlock[] closureArguments = ((GrMethodCallExpression)element).getClosureArguments();
        if (closureArguments.length != 1) return false;
        final GrClosableBlock block = closureArguments[0];
        if (block.getLBrace() == null || block.getRBrace() == null) return false;
        return true;
      }
    };
  }
}
