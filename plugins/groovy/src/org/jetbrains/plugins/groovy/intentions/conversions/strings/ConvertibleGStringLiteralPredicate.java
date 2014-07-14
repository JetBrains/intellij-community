/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.conversions.strings;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;


class ConvertibleGStringLiteralPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrLiteral)) return false;
    if (ErrorUtil.containsError(element)) return false;

    @NonNls final String text = element.getText();

    if (text.charAt(0) != '"') return false;
    for (PsiElement child : element.getChildren()) {
      if (child instanceof GrStringInjection) {
        GrClosableBlock block = ((GrStringInjection)child).getClosableBlock();
        if (block != null && !checkClosure(block)) return false;
      }
    }
    return true;
  }

  private static boolean checkClosure(GrClosableBlock block) {
    if (block.hasParametersSection()) return false;
    final GrStatement[] statements = block.getStatements();
    if (statements.length != 1) return false;
    return statements[0] instanceof GrExpression || statements[0] instanceof GrReturnStatement;
  }
}
