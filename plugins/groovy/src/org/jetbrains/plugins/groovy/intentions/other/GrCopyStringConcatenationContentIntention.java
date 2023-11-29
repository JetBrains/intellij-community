/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.other;

import com.intellij.modcommand.*;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * @author Max Medvedev
 */
public class GrCopyStringConcatenationContentIntention extends PsiBasedModCommandAction<GrExpression> {
  public GrCopyStringConcatenationContentIntention() {
    super(GrExpression.class);
  }

  @Override
  protected boolean isElementApplicable(@NotNull GrExpression element, @NotNull ActionContext context) {
    if (element instanceof GrLiteral literal && literal.getValue() instanceof String) return true;
    if (!(element instanceof GrBinaryExpression binOp)) return false;
    if (binOp.getOperationTokenType() != GroovyTokenTypes.mPLUS) return false;
    var left = binOp.getLeftOperand();
    var right = binOp.getRightOperand();
    return right != null && isElementApplicable(left, context) && isElementApplicable(right, context);
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull GrExpression element) {
    final StringBuilder buffer = new StringBuilder();
    getValue(element, buffer);
    return ModCommand.copyToClipboard(buffer.toString());
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull GrExpression element) {
    if (element instanceof GrLiteral) {
      return Presentation.of(GroovyIntentionsBundle.message("gr.copy.string.literal.content.intention.text"));
    }
    return super.getPresentation(context, element);
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyIntentionsBundle.message("gr.copy.string.concatenation.content.intention.family.name");
  }

  private static void getValue(PsiElement element, StringBuilder buffer) {
    if (element instanceof GrLiteral literal) {
      buffer.append(literal.getValue());
    }
    else if (element instanceof GrBinaryExpression binOp) {
      getValue(binOp.getLeftOperand(), buffer);
      getValue(binOp.getRightOperand(), buffer);
    }
  }
}
