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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ven
 */
public class GroovyNamesUtil {

  private static final Pattern PATTERN = Pattern.compile("[A-Za-z][a-z0-9]*");

  private GroovyNamesUtil() {
  }

  public static boolean isIdentifier(@Nullable String text) {
    if (text == null) return false;

    Lexer lexer = new GroovyLexer();
    lexer.start(text);
    if (lexer.getTokenType() != GroovyTokenTypes.mIDENT) return false;
    lexer.advance();
    return lexer.getTokenType() == null;
  }

  public static boolean isValidReference(@Nullable String text, boolean afterDot, Project project) {
    if (text == null) return false;

    try {
      GroovyPsiElementFactory.getInstance(project).createReferenceExpressionFromText(afterDot ? "foo." + text : text);
      return true;
    }
    catch (Exception e) {
      return false;
    }
  }

  public static ArrayList<String> camelizeString(String str) {
    ArrayList<String> res = new ArrayList<>();
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

  public static String deleteNonLetterFromString(String tempString) {
    return tempString.replaceAll("[^a-zA-Z]", "");
  }

  public static String fromLowerLetter(String str) {
    if (str.isEmpty()) return "";
    if (str.length() == 1) return str.toLowerCase();
    char c = Character.toLowerCase(str.charAt(0));
    if (c == str.charAt(0)) return str;
    return c + str.substring(1);
  }

  public static String camelToSnake(final String string) {
    return StringUtil.join(camelizeString(string), s -> StringUtil.decapitalize(s), "-");
  }

  public static boolean isKeyword(@NotNull String name) {
    final GroovyLexer lexer = new GroovyLexer();
    lexer.start(name);
    final IElementType type = lexer.getTokenType();
    return TokenSets.KEYWORDS.contains(type);
  }

  public static String[] getMethodArgumentsNames(Project project, PsiType[] types) {
    Set<String> uniqNames = new LinkedHashSet<>();
    Set<String> nonUniqNames = new THashSet<>();
    for (PsiType type : types) {
      final SuggestedNameInfo nameInfo =
        JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.PARAMETER, null, null, type);

      final String name = nameInfo.names[0];
      if (uniqNames.contains(name)) {
        int i = 2;
        while (uniqNames.contains(name + i)) i++;
        uniqNames.add(name + i);
        nonUniqNames.add(name);
      } else {
        uniqNames.add(name);
      }
    }

    final String[] result = new String[uniqNames.size()];
    int i = 0;
    for (String name : uniqNames) {
      result[i] = nonUniqNames.contains(name) ? name + 1 : name;
      i++;
    }
    return result;
  }
}
