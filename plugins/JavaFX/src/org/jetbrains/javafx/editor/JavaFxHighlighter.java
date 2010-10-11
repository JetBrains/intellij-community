/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.javafx.editor;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * Higlighter of JavaFx syntax
 *
 * @author andrey, Alexey.Ivanov
 */
public class JavaFxHighlighter extends SyntaxHighlighterBase {
  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<IElementType, TextAttributesKey>();
  private static final TextAttributesKey CUSTOM_KEYWORD2_ATTRIBUTES =
    TextAttributesKey.createTextAttributesKey("CUSTOM_KEYWORD2_ATTRIBUTES");

  static {
    fillMap(ATTRIBUTES, JavaFxTokenTypes.BLOCK_COMMENTS, SyntaxHighlighterColors.JAVA_BLOCK_COMMENT);
    fillMap(ATTRIBUTES, JavaFxTokenTypes.LINE_COMMENTS, SyntaxHighlighterColors.LINE_COMMENT);
    fillMap(ATTRIBUTES, JavaFxTokenTypes.ALL_WORDS, SyntaxHighlighterColors.KEYWORD);
    fillMap(ATTRIBUTES, JavaFxTokenTypes.NUMBERS, SyntaxHighlighterColors.NUMBER);
    fillMap(ATTRIBUTES, JavaFxTokenTypes.ALL_STRINGS, SyntaxHighlighterColors.STRING);
    fillMap(ATTRIBUTES, JavaFxTokenTypes.BRACES, SyntaxHighlighterColors.BRACES);
    //fillMap(ATTRIBUTES, JavaFxTokenTypes.TYPES, CUSTOM_KEYWORD2_ATTRIBUTES);
  }

  @NotNull
  public Lexer getHighlightingLexer() {
    return new JavaFxHighlightingLexer();
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType elementType) {
    return pack(ATTRIBUTES.get(elementType));
  }
}
