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
package com.intellij.ide.highlighter.custom

import com.intellij.psi.tree.IElementType
import static com.intellij.psi.CustomHighlighterTokenType.*

/**
 * @author peter
 */
class CustomFileTypeLexerTest extends CustomFileTypeLexerTestBase {
  @Override
  protected SyntaxTable createSyntaxTable() {
    SyntaxTable table = new SyntaxTable();

    table.lineComment = ''

    table.addKeyword1("if");
    table.addKeyword1("then");
    table.addKeyword2("return");
    table.addKeyword1("length");
    table.addKeyword1("sysvar ");

    return table;

  }

  public void testSpacesInsideKeywords() {
    checkTypesAndTokens('if length(variable)then return 1',
                        [KEYWORD_1, WHITESPACE, KEYWORD_1, CHARACTER, IDENTIFIER, CHARACTER, KEYWORD_1, WHITESPACE, KEYWORD_2, WHITESPACE, NUMBER] as IElementType[],
                        ['if', ' ', 'length', '(', 'variable', ')', 'then', ' ', 'return', ' ', '1'] as String[])

  }
}
