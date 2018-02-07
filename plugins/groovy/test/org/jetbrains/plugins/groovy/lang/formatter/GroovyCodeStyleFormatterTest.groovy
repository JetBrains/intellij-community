/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings
import org.jetbrains.plugins.groovy.util.TestUtils

import java.lang.reflect.Field
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * @author peter
 */
class GroovyCodeStyleFormatterTest extends GroovyFormatterTestCase {
  private static final String OPTION_START = "<option>"
  private static final String OPTION_END = "</option>"
  private static final Pattern PATTERN = ~"$OPTION_START(\\w+=(true|false+|\\d|\\w+))$OPTION_END\n"

  final String basePath = TestUtils.testDataPath + "groovy/codeStyle/"

  private void doTest() throws Throwable {
    final List<String> data = TestUtils.readInput(testDataPath + getTestName(true) + ".test")
    String input = data[0]
    while (true) {
      def (String name, String value, Integer matcherEnd) = parseOption(input)
      if (!name || !value) break

      def (Field field, Object settingObj) = findSettings(name)
      field.set(settingObj, evaluateValue(value))
      input = input.substring(matcherEnd)
    }

    checkFormatting(input, data[1])
  }

  @NotNull
  static List parseOption(String input) {
    final Matcher matcher = PATTERN.matcher(input)
    if (!matcher.find()) return [null, null, null]
    final String[] strings = matcher.group(1).split("=")
    return [strings[0], strings[1], matcher.end()]
  }

  private List findSettings(String name) {
    CodeStyleSettings settings = CodeStyle.getSettings(project)
    try {
      return [CommonCodeStyleSettings.getField(name), settings.getCommonSettings(GroovyLanguage.INSTANCE)]
    }
    catch (NoSuchFieldException ignored) {
      return [GroovyCodeStyleSettings.getField(name), settings.getCustomSettings(GroovyCodeStyleSettings)]
    }
  }

  @Nullable
  private static Object evaluateValue(String value) {
    if (value == "true" || value == "false") {
      return Boolean.parseBoolean(value)
    }
    else {
      try {
        return Integer.parseInt(value)
      }
      catch (NumberFormatException ignored) {
        return CommonCodeStyleSettings.getField(value).get(value)
      }
    }
  }

  void testClass_decl1() throws Throwable { doTest() }

  void testClass_decl2() throws Throwable { doTest() }

  void testClass_decl3() throws Throwable { doTest() }

  void testClass_decl4() throws Throwable { doTest() }

  void testComm_at_first_column1() throws Throwable { doTest() }

  void testComm_at_first_column2() throws Throwable { doTest() }

  void testFor1() throws Throwable { doTest() }

  void testFor2() throws Throwable { doTest() }

  void testGRVY_1134() throws Throwable { doTest() }

  void testIf1() throws Throwable { doTest() }

  void testMethod_call_par1() throws Throwable { doTest() }

  void testMethod_call_par2() throws Throwable { doTest() }

  void testMethod_decl1() throws Throwable { doTest() }

  void testMethod_decl2() throws Throwable { doTest() }

  void testMethod_decl_par1() throws Throwable { doTest() }

  void testSwitch1() throws Throwable { doTest() }

  void testSynch1() throws Throwable { doTest() }

  void testTry1() throws Throwable { doTest() }

  void testTry2() throws Throwable { doTest() }

  void testWhile1() throws Throwable { doTest() }

  void testWhile2() throws Throwable { doTest() }

  void testWithin_brackets1() throws Throwable { doTest() }

  void testSpace_in_named_arg_true() throws Throwable { doTest() }

  void testSpace_in_named_arg_false() throws Throwable { doTest() }

  void testSpaceInNamedArgBeforeColon() { doTest() }

  void testAnonymousVsLBraceOnNewLine() { doTest() }

  void testBracesNextLine() { doTest() }

  void testBracesNextLineShifted() { doTest() }

  void testBracesNextLineShifted2() { doTest() }

  void testBracesEndLine() { doTest() }
}
