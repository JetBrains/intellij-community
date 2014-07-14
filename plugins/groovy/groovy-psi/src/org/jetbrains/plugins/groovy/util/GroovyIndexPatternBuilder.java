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
package org.jetbrains.plugins.groovy.util;

import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * User: Dmitry.Krasilschikov
 * Date: 16.07.2008
 */
public class GroovyIndexPatternBuilder implements IndexPatternBuilder {
    @Override
    public Lexer getIndexingLexer(@NotNull PsiFile file) {
        if (file instanceof GroovyFile) {
            return new GroovyLexer();
        }
        return null;
    }

    @Override
    public TokenSet getCommentTokenSet(@NotNull PsiFile file) {
      return TokenSets.ALL_COMMENT_TOKENS;
    }

    @Override
    public int getCommentStartDelta(IElementType tokenType) {
      return 0;
    }

    @Override
    public int getCommentEndDelta(IElementType tokenType) {
      return tokenType == GroovyTokenTypes.mML_COMMENT ? 2 : 0;
    }
}
