/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.tokenizer.SuppressibleSpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyStringLiteralManipulator;

/**
 * @author peter
 */
public class GroovySpellcheckingStrategy extends SuppressibleSpellcheckingStrategy {
  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof GrNamedElement) {
      final PsiElement name = ((GrNamedElement)element).getNameIdentifierGroovy();
      if (TokenSets.STRING_LITERAL_SET.contains(name.getNode().getElementType())) {
        return new Tokenizer<GrNamedElement>() {
          @Override
          public void tokenize(@NotNull GrNamedElement element, TokenConsumer consumer) {
            String text = name.getText();
            TextRange range = GroovyStringLiteralManipulator.getLiteralRange(text);
            consumer.consumeToken(name, text, false, 0, range, PlainTextSplitter.getInstance());
          }
        };
        
      }
    }
    return super.getTokenizer(element);
  }

  @Override
  public boolean isSuppressedFor(PsiElement element, String name) {
    return GroovySuppressableInspectionTool.getElementToolSuppressedIn(element, name) != null;
  }

  @Override
  public SuppressIntentionAction[] getSuppressActions(PsiElement element, String name) {
    return GroovySuppressableInspectionTool.getSuppressActions(name);
  }
}
