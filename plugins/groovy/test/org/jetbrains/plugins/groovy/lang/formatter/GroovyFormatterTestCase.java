/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings
import org.jetbrains.plugins.groovy.util.TestUtils

import java.lang.reflect.Field
import java.util.regex.Matcher
import java.util.regex.Pattern

abstract class GroovyFormatterTestCase extends LightJavaCodeInsightFixtureTestCase {
  private static final Logger LOG = Logger.getInstance(GroovyFormatterTestCase.class)
  private static final String OPTION_START = "<option>"
  private static final String OPTION_END = "</option>"
  private static final Pattern PATTERN = ~"$OPTION_START(\\w+=(true|false+|\\d|\\w+))$OPTION_END\n"
  protected CodeStyleSettings myTempSettings

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    setSettings(getProject())

    groovySettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    groovySettings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    groovySettings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
  }

  protected CommonCodeStyleSettings getGroovySettings() {
    return myTempSettings.getCommonSettings(GroovyLanguage.INSTANCE)
  }

  protected GroovyCodeStyleSettings getGroovyCustomSettings() {
    return myTempSettings.getCustomSettings(GroovyCodeStyleSettings)
  }

  protected void setSettings(Project project) {
    assertNull(myTempSettings)
    CodeStyleSettings settings = CodeStyle.getSettings(project)
    myTempSettings = settings

    CommonCodeStyleSettings.IndentOptions gr = myTempSettings.getIndentOptions(GroovyFileType.GROOVY_FILE_TYPE)
    assertNotSame(gr, settings.OTHER_INDENT_OPTIONS)
    gr.INDENT_SIZE = 2
    gr.CONTINUATION_INDENT_SIZE = 4
    gr.TAB_SIZE = 2
    myTempSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 3
  }

  protected void checkFormatting(String fileText, String expected) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText)
    checkFormatting(expected)
  }

  protected void doFormat(final PsiFile file) {
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          void run() {
            try {
              TextRange myTextRange = file.getTextRange()
              CodeStyleManager.getInstance(file.getProject()).reformatText(file, myTextRange.getStartOffset(), myTextRange.getEndOffset())
            }
            catch (IncorrectOperationException e) {
              LOG.error(e)
            }
          }
        })
      }
    }, null, null)
  }

  protected void checkFormatting(String expected) {
    doFormat(myFixture.getFile())
    myFixture.checkResult(expected)
  }

  void doTest(String fileName) throws Throwable {
    final List<String> data = TestUtils.readInput(testDataPath + fileName)
    String inputWithOptions = data[0]
    String input = inputWithOptions
    while (true) {
      def (String name, String value, Integer matcherEnd) = parseOption(input)
      if (!name || !value) break

      def (Field field, Object settingObj) = findSettings(name)
      field.set(settingObj, evaluateValue(value))
      input = input.substring(matcherEnd)
    }

    checkFormattingByFile(input, inputWithOptions, fileName)
  }

  protected void checkFormattingByFile(String input, String inputWithOptions, String path) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, input)
    doFormat(myFixture.getFile())
    final String prefix = inputWithOptions + '\n-----\n'
    myFixture.configureByText('test.txt', prefix + myFixture.getFile().getText())
    myFixture.checkResultByFile(path, false)
  }

  @NotNull
  static List parseOption(String input) {
    final Matcher matcher = PATTERN.matcher(input)
    if (!matcher.find()) return [null, null, null]
    final String[] strings = matcher.group(1).split("=")
    return [strings[0], strings[1], matcher.end()]
  }

  private List findSettings(String name) {
    return findField(CommonCodeStyleSettings, name)?.with { [it, getGroovySettings()] }
      ?: findField(GroovyCodeStyleSettings, name)?.with { [it, getGroovyCustomSettings()] }
             ?: findField(CommonCodeStyleSettings.IndentOptions, name)?.with { [it, getGroovySettings().getIndentOptions()] }
  }

  private static Field findField(Class<?> clazz, String name) {
    return clazz.fields.find { it.name == name }
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
}
