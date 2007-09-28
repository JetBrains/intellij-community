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

package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author ven
 */
public class GroovyNamesUtil {

  public static boolean isIdentifier(String text) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (text == null) return false;

    Lexer lexer = new GroovyLexer();
    lexer.start(text, 0, text.length(), 0);
    if (lexer.getTokenType() != GroovyTokenTypes.mIDENT) return false;
    lexer.advance();
    return lexer.getTokenType() == null;
  }

  public static boolean isKeyword(String text) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Lexer lexer = new GroovyLexer();
    lexer.start(text,0,text.length(),0);
    if (lexer.getTokenType() == null || !GroovyTokenTypes.KEYWORDS.contains(lexer.getTokenType())) return false;
    lexer.advance();
    return lexer.getTokenType() == null;
  }


}
