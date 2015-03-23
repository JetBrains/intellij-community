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

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.LookAheadLexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.TokenSet;

import java.io.Reader;

/**
 * @author ilyas
 */
public class GroovyLexer extends LookAheadLexer {
  private static final TokenSet tokensToMerge = TokenSet.create(
    GroovyTokenTypes.mSL_COMMENT,
    GroovyTokenTypes.mML_COMMENT,
    GroovyTokenTypes.mREGEX_CONTENT,
    GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT,
    TokenType.WHITE_SPACE,
    GroovyTokenTypes.mGSTRING_CONTENT
  );

  public GroovyLexer() {
    super(new MergingLexerAdapter(new FlexAdapter(new _GroovyLexer((Reader) null)), tokensToMerge));
  }
}
