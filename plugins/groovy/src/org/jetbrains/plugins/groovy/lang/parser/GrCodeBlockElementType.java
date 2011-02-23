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
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IErrorCounterReparseableElementType;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author peter
 */
class GrCodeBlockElementType extends IErrorCounterReparseableElementType {

  GrCodeBlockElementType(String debugName) {
    super(debugName, GroovyFileType.GROOVY_LANGUAGE);
  }

  @Override
  public ASTNode createNode(final CharSequence text) {
    return new LazyParseablePsiElement(this, text);
  }

  @Override
  public int getErrorsCount(final CharSequence seq, final Project project) {
    final Lexer lexer = new GroovyLexer();

    lexer.start(seq);
    if (lexer.getTokenType() != GroovyTokenTypes.mLCURLY) return FATAL_ERROR;
    lexer.advance();
    int balance = 1;
    while (true) {
      IElementType type = lexer.getTokenType();
      if (type == null) break;
      if (balance == 0) return FATAL_ERROR;
      if (type == GroovyTokenTypes.mLCURLY) {
        balance++;
      }
      else if (type == GroovyTokenTypes.mRCURLY) {
        balance--;
      }
      lexer.advance();
    }
    return balance;
  }
}
