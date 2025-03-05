// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyStringLiteralManipulator;

public final class GrArgumentLabelManipulator extends AbstractElementManipulator<GrArgumentLabel> {
  @Override
  public @NotNull TextRange getRangeInElement(@NotNull GrArgumentLabel element) {
    final PsiElement nameElement = element.getNameElement();
    if (nameElement instanceof LeafPsiElement && TokenSets.STRING_LITERAL_SET.contains(((LeafPsiElement)nameElement).getElementType())) {
      return GroovyStringLiteralManipulator.getLiteralRange(nameElement.getText());
    }

    return super.getRangeInElement(element);
  }

  @Override
  public GrArgumentLabel handleContentChange(@NotNull GrArgumentLabel element, @NotNull TextRange range, String newContent)
    throws IncorrectOperationException {
    element.setName(newContent);
    return element;
  }
}
