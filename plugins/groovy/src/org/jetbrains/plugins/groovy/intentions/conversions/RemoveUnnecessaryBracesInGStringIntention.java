/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;

/**
 * @author Maxim.Medvedev
 */
public class RemoveUnnecessaryBracesInGStringIntention extends Intention {
  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    performIntention(element, false);
  }

  public static void performIntention(PsiElement element, boolean checkAvailable) {
    if (checkAvailable && !MyPredicate.isIntentionAvailable(element)) return;
    for (PsiElement child : element.getChildren()) {
      if (MyPredicate.checkClosableBlock(child)) {
        final GrReferenceExpression refExpr = (GrReferenceExpression)((GrClosableBlock)child).getStatements()[0];
        final GrReferenceExpression copy = (GrReferenceExpression)refExpr.copy();
        ((GrClosableBlock)child).replaceWithExpression(copy, false);
      }
    }
  }

  static class MyPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
      return isIntentionAvailable(element);
    }

    public static boolean isIntentionAvailable(PsiElement element) {
      if (!(element instanceof GrString)) return false;

      if (ErrorUtil.containsError(element)) return false;

      for (PsiElement child : element.getChildren()) {
        if (checkClosableBlock(child)) return true;
      }
      return false;
    }

    public static boolean checkClosableBlock(PsiElement element) {
      if (!(element instanceof GrClosableBlock)) return false;
      GrClosableBlock block = (GrClosableBlock)element;

      final GrStatement[] statements = block.getStatements();
      if (statements.length != 1) return false;

      if (!(statements[0] instanceof GrReferenceExpression)) return false;

      final PsiElement next = block.getNextSibling();
      if (!(next instanceof LeafPsiElement)) return false;

      char nextChar = next.getText().charAt(0);
      if (nextChar == '"' || nextChar == '$') {
        return true;
      }
      final GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(element.getProject());
      final GrExpression gString = elementFactory.createExpressionFromText("\"$" + statements[0].getText() + nextChar + '"');
      final GrReferenceExpression refExpr = (GrReferenceExpression)statements[0];
      final PsiElement refExprCopy = gString.getChildren()[0];
      if (!(refExprCopy instanceof GrReferenceExpression)) return false;

      return Comparing.equal(refExpr.getName(), ((GrReferenceExpression)refExprCopy).getName());
    }
  }
}


