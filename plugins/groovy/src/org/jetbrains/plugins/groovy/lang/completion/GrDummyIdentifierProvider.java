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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

/**
 * Created by Max Medvedev on 14/05/14
 */
public class GrDummyIdentifierProvider {
  public static final String DUMMY_IDENTIFIER_DECAPITALIZED = StringUtil.decapitalize(CompletionUtil.DUMMY_IDENTIFIER);

  private final CompletionInitializationContext myContext;

  public GrDummyIdentifierProvider(@NotNull CompletionInitializationContext context) {
    myContext = context;
  }

  @Nullable
  public String getIdentifier() {
    if (myContext.getCompletionType() == CompletionType.BASIC && myContext.getFile() instanceof GroovyFile) {
      PsiElement position = myContext.getFile().findElementAt(myContext.getStartOffset());
      if (position != null &&
          position.getParent() instanceof GrVariable &&
          position == ((GrVariable)position.getParent()).getNameIdentifierGroovy() ||

          position != null &&
          position.getParent() instanceof GrAnnotationNameValuePair &&
          position == ((GrAnnotationNameValuePair)position.getParent()).getNameIdentifierGroovy()) {

        return CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;
      }
      else if (isIdentifierBeforeLParenth()) {
        return setCorrectCase() + ";";
      }
      else if (GroovyCompletionUtil.isInPossibleClosureParameter(position)) {
        return setCorrectCase() + "->";
      }
      else if (isBeforeAssign()) {
        return CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;
      }
      else {
        return setCorrectCase();
      }
    }
    return null;
  }

  private boolean isBeforeAssign() {
    //<caret>String name=
    HighlighterIterator iterator = ((EditorEx)myContext.getEditor()).getHighlighter().createIterator(myContext.getStartOffset());
    if (iterator.atEnd()) return false;

    if (iterator.getTokenType() == GroovyTokenTypes.mIDENT) {
      iterator.advance();
    }

    while (!iterator.atEnd() && TokenSets.WHITE_SPACES_OR_COMMENTS.contains(iterator.getTokenType())) {
      iterator.advance();
    }

    return !iterator.atEnd() && iterator.getTokenType() == GroovyTokenTypes.mASSIGN;
  }

  @NotNull
  private String setCorrectCase() {
    final PsiElement element = myContext.getFile().findElementAt(myContext.getStartOffset());
    if (element == null) return DUMMY_IDENTIFIER_DECAPITALIZED;

    final String text = element.getText();
    if (text.isEmpty()) return DUMMY_IDENTIFIER_DECAPITALIZED;

    return Character.isUpperCase(text.charAt(0)) ? CompletionInitializationContext.DUMMY_IDENTIFIER : DUMMY_IDENTIFIER_DECAPITALIZED;
  }

  private boolean isIdentifierBeforeLParenth() { //<caret>String name=
    HighlighterIterator iterator = ((EditorEx)myContext.getEditor()).getHighlighter().createIterator(myContext.getStartOffset());
    if (iterator.atEnd()) return false;

    if (iterator.getTokenType() == GroovyTokenTypes.mIDENT) {
      iterator.advance();
    }

    while (!iterator.atEnd() && TokenSets.WHITE_SPACES_OR_COMMENTS.contains(iterator.getTokenType())) {
      iterator.advance();
    }

    return !iterator.atEnd() && iterator.getTokenType() == GroovyTokenTypes.mLPAREN;
  }
}
