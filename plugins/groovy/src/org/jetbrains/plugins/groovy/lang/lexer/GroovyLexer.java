/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.TokenSet;

/**
 * @author ilyas
 */
public class GroovyLexer extends MergingLexerAdapter {
  private static TokenSet tokensToMerge = TokenSet.create(
                    GroovyTokenTypes.mSL_COMMENT,
                    GroovyTokenTypes.mML_COMMENT,
                    GroovyTokenTypes.mWS,
                    GroovyTokenTypes.mWRONG_GSTRING_LITERAL
            );

  public GroovyLexer() {
    super(new GroovyFlexLexer(),
        tokensToMerge);
  }

}
