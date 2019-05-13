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

/**
 * @author peter
 */
public class GrArgumentLabelManipulator extends AbstractElementManipulator<GrArgumentLabel> {
  @NotNull
  @Override
  public TextRange getRangeInElement(@NotNull GrArgumentLabel element) {
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
