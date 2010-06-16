/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ven
 */
public class GroovyNamesUtil {

  private static final Pattern PATTERN = Pattern.compile("[A-Za-z][a-z]*");

  private GroovyNamesUtil() {
  }

  public static boolean isIdentifier(String text) {
    if (text == null) return false;

    Lexer lexer = new GroovyLexer();
    lexer.start(text);
    if (lexer.getTokenType() != GroovyTokenTypes.mIDENT) return false;
    lexer.advance();
    return lexer.getTokenType() == null;
  }

  public static boolean isKeyword(String text) {
    Lexer lexer = new GroovyLexer();
    lexer.start(text);
    if (lexer.getTokenType() == null || !GroovyTokenTypes.KEYWORDS.contains(lexer.getTokenType())) return false;
    lexer.advance();
    return lexer.getTokenType() == null;
  }

  public static ArrayList<String> camelizeString(String str) {
    ArrayList<String> res = new ArrayList<String>();
    StringBuilder sb = new StringBuilder();
    Matcher matcher = PATTERN.matcher(str);
    
    while (matcher.find()) {
      res.add(matcher.group().toLowerCase());
      sb.append(matcher.group());
    }

    if (!isIdentifier(sb.toString())) {
      res.clear();
    }
    return res;
  }

  static String deleteNonLetterFromString(String tempString) {
    return tempString.replaceAll("[^a-zA-Z]", "");
  }

  static String fromLowerLetter(String str) {
    if (str.length() == 0) return "";
    if (str.length() == 1) return str.toLowerCase();
    char c = Character.toLowerCase(str.charAt(0));
    if (c == str.charAt(0)) return str;
    return c + str.substring(1);
  }

  public static String camelToSnake(final String string) {
    return StringUtil.join(camelizeString(string), new Function<String, String>() {
      public String fun(final String s) {
        return StringUtil.decapitalize(s);
      }
    }, "-");
  }
}
