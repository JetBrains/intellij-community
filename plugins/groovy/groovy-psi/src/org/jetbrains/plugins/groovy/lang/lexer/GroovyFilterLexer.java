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
package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;

/**
 * @author Maxim.Medvedev
 */
public class GroovyFilterLexer extends BaseFilterLexer {

  public GroovyFilterLexer(Lexer originalLexer, OccurrenceConsumer occurrenceConsumer) {
    super(originalLexer, occurrenceConsumer);
  }


  @Override
  public void advance() {
    final IElementType tokenType = getDelegate().getTokenType();

    if (tokenType == GroovyTokenTypes.mIDENT || TokenSets.KEYWORDS.contains(tokenType)) {
      addOccurrenceInToken(UsageSearchContext.IN_CODE);
    }
    else if (TokenSets.STRING_LITERALS.contains(tokenType)) {
      scanWordsInToken(UsageSearchContext.IN_STRINGS | UsageSearchContext.IN_FOREIGN_LANGUAGES, false, true);
    }
    else if (TokenSets.COMMENT_SET.contains(tokenType)) {
      scanWordsInToken(UsageSearchContext.IN_COMMENTS, false, false);
      advanceTodoItemCountsInToken();
    }
    else {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT, false, false);
    }

    getDelegate().advance();
  }
}
