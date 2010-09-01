/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.formatter;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public class GroovyCodeStyleFormatterTest extends GroovyFormatterTestCase {
  private static final String OPTION_START = "<option>";
  private static final String OPTION_END = "</option>";
  private static final Pattern PATTERN = Pattern.compile(OPTION_START + "(\\w+=(true|false+|\\d|\\w+))" + OPTION_END + "\n");

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/codeStyle/";
  }

  private void doTest() throws Throwable {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
    String input = data.get(0);
    while (true) {
      final Matcher matcher = PATTERN.matcher(input);
      if (!matcher.find()) break;
      final String[] strings = matcher.group(1).split("=");
      String name = strings[0];
      final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
      final Field field = CodeStyleSettings.class.getField(name);
      final String value = strings[1];
      if ("true".equals(value) || "false".equals(value)) {
        field.set(settings, Boolean.parseBoolean(value));
      } else {
        try {
          field.set(settings, Integer.parseInt(value));
        }
        catch (NumberFormatException e) {
          field.set(settings, CodeStyleSettings.class.getField(value).get(value));
        }
      }
      input = input.substring(matcher.end());
    }

    checkFormatting(input, data.get(1));
  }

  public void testClass_decl1() throws Throwable { doTest(); }
  public void testClass_decl2() throws Throwable { doTest(); }
  public void testClass_decl3() throws Throwable { doTest(); }
  public void testClass_decl4() throws Throwable { doTest(); }
  public void testComm_at_first_column1() throws Throwable { doTest(); }
  public void testComm_at_first_column2() throws Throwable { doTest(); }
  public void testFor1() throws Throwable { doTest(); }
  public void testFor2() throws Throwable { doTest(); }
  public void testGRVY_1134() throws Throwable { doTest(); }
  public void testIf1() throws Throwable { doTest(); }
  public void testMethod_call_par1() throws Throwable { doTest(); }
  public void testMethod_call_par2() throws Throwable { doTest(); }
  public void testMethod_decl1() throws Throwable { doTest(); }
  public void testMethod_decl2() throws Throwable { doTest(); }
  public void testMethod_decl_par1() throws Throwable { doTest(); }
  public void testSwitch1() throws Throwable { doTest(); }
  public void testSynch1() throws Throwable { doTest(); }
  public void testTry1() throws Throwable { doTest(); }
  public void testTry2() throws Throwable { doTest(); }
  public void testWhile1() throws Throwable { doTest(); }
  public void testWhile2() throws Throwable { doTest(); }
  public void testWithin_brackets1() throws Throwable { doTest(); }

}
