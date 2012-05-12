/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.lexer.LookAheadLexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.TokenSet;

import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 * @author ilyas
 */
public class GroovyLexer extends LookAheadLexer {
  private static final TokenSet tokensToMerge = TokenSet.create(
    mSL_COMMENT,
    mML_COMMENT,
    mREGEX_CONTENT,
    mDOLLAR_SLASH_REGEX_CONTENT,
    WHITE_SPACE,
    mGSTRING_CONTENT,
    mDOLLAR_SLASH_REGEX_CONTENT,
    mREGEX_CONTENT
  );

  public GroovyLexer() {
    super(new MergingLexerAdapter(new GroovyFlexLexer(), tokensToMerge));
  }
}
