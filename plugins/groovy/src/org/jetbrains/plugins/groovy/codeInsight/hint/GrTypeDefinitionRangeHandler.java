// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.hint;

import com.intellij.codeInsight.hint.DeclarationRangeHandler;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

public final class GrTypeDefinitionRangeHandler implements DeclarationRangeHandler<GrTypeDefinition>{
  @Override
  public @NotNull TextRange getDeclarationRange(@NotNull GrTypeDefinition aClass) {
    if (aClass instanceof GrAnonymousClassDefinition){
      GrNewExpression call = (GrNewExpression)aClass.getParent();
      int startOffset = call.getTextRange().getStartOffset();
      int endOffset = call.getArgumentList().getTextRange().getEndOffset();
      return new TextRange(startOffset, endOffset);
    }
    else{
      final PsiModifierList modifierList = aClass.getModifierList();
      int startOffset = modifierList == null ? aClass.getTextRange().getStartOffset() : modifierList.getTextRange().getStartOffset();
      final GrImplementsClause implementsList = aClass.getImplementsClause();
      int endOffset = implementsList == null ? aClass.getTextRange().getEndOffset() : implementsList.getTextRange().getEndOffset();
      return new TextRange(startOffset, endOffset);
    }

  }
}
