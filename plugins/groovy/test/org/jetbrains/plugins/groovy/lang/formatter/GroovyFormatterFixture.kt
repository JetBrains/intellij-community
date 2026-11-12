// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.codehaus.groovy.runtime.StringGroovyMethods
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.Assertions.assertNotSame
import java.io.Serializable
import java.lang.reflect.Field

/**
 * The fixture of the Groovy formatter tests. Create it with [groovyFormatterFixture].
 *
 * The fixture sets the Groovy code style of the tests: an indent of 2, a continuation indent of 4,
 * the braces at the end of the line, and 3 classes for an import on demand.
 * The members format the file of the fixture in a command on the EDT.
 */
@TestOnly
class GroovyFormatterFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  /**
   * The code style settings of the project. A test can change them. The code insight fixture restores them after the test.
   */
  val tempSettings: CodeStyleSettings,
) {
  val groovySettings: CommonCodeStyleSettings
    get() = tempSettings.getCommonSettings(GroovyLanguage)

  val groovyCustomSettings: GroovyCodeStyleSettings
    get() = tempSettings.getCustomSettings(GroovyCodeStyleSettings::class.java)

  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  fun checkFormatting(fileText: String, expected: String) {
    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText)
    checkFormatting(expected)
  }

  /**
   * Formats the file of the fixture and compares it with [expected].
   */
  fun checkFormatting(expected: String) {
    doFormat(fixture.file)
    fixture.checkResult(expected)
  }

  fun doFormat(file: PsiFile) {
    runInEdtAndWait {
      val range = file.textRange
      reformat(file, range.startOffset, range.endOffset)
    }
  }

  fun doFormatRange(file: PsiFile, start: Int, end: Int) {
    runInEdtAndWait {
      reformat(file, start, end)
    }
  }

  /**
   * Reads the test data file [fileName]. Its first part can start with `<option>name=value</option>` lines.
   * Each line sets a field of the common, the Groovy or the indent settings.
   * Then the fixture formats the rest of the first part and compares the options, the `-----` line and the result with the file.
   */
  fun doTest(fileName: String) {
    val inputWithOptions = TestUtils.readInput(fixture.testDataPath + fileName)[0]
    var input = inputWithOptions
    while (true) {
      val option = parseOption(input)
      val name = option[0] as String?
      val value = option[1] as String?
      val matcherEnd = option[2] as Int?
      if (!StringGroovyMethods.asBoolean(name) || !StringGroovyMethods.asBoolean(value)) break

      val (field, settingObj) = findSettings(name!!)!!
      field.set(settingObj, evaluateValue(value!!))
      input = input.substring(matcherEnd!!)
    }
    checkFormattingByFile(input, inputWithOptions, fileName)
  }

  fun checkFormattingByFile(input: String, inputWithOptions: String, path: String) {
    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, input)
    runInEdtAndWait {
      doFormat(fixture.file)
      fixture.configureByText("test.txt", inputWithOptions + "\n-----\n" + fixture.file.text)
    }
    fixture.checkResultByFile(path, false)
  }

  private fun reformat(file: PsiFile, start: Int, end: Int) {
    CommandProcessor.getInstance().executeCommand(fixture.project, {
      ApplicationManager.getApplication().runWriteAction {
        CodeStyleManager.getInstance(file.project).reformatText(file, start, end)
      }
    }, null, null)
  }

  private fun findSettings(name: String): Pair<Field, Any>? {
    findField(CommonCodeStyleSettings::class.java, name)?.let { return it to groovySettings }
    findField(GroovyCodeStyleSettings::class.java, name)?.let { return it to groovyCustomSettings }
    return findField(CommonCodeStyleSettings.IndentOptions::class.java, name)?.let { it to groovySettings.indentOptions!! }
  }

  companion object {
    private const val OPTION_START = "<option>"
    private const val OPTION_END = "</option>"
    private val PATTERN = StringGroovyMethods.bitwiseNegate("$OPTION_START(\\w+=(true|false+|\\d|\\w+))$OPTION_END\n")

    /**
     * Returns the name, the value and the end offset of the first `<option>` line of [input], or three `null` values.
     */
    @JvmStatic
    fun parseOption(input: String): List<Serializable?> {
      val matcher = PATTERN.matcher(input)
      if (!matcher.find()) return listOf(null, null, null)
      val strings = matcher.group(1).split("=")
      return listOf(strings[0], strings[1], matcher.end())
    }

    private fun findField(clazz: Class<*>, name: String): Field? = clazz.fields.find { it.name == name }

    private fun evaluateValue(value: String): Any? {
      if (value == "true" || value == "false") {
        return value.toBoolean()
      }
      return value.toIntOrNull() ?: CommonCodeStyleSettings::class.java.getField(value).get(value)
    }
  }
}

/**
 * Creates a [GroovyFormatterFixture] for one test. Declare it as an instance field.
 * [testDataPath] is the absolute test data path. By default, it is the community root.
 */
@TestOnly
@JvmOverloads
fun groovyFormatterFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<GroovyFormatterFixture> = testFixture("groovyFormatterFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  if (testDataPath != null) {
    fixture.testDataPath = testDataPath
  }
  val settings = CodeStyle.getSettings(fixture.project)
  val indentOptions = settings.getIndentOptions(GroovyFileType.GROOVY_FILE_TYPE)
  assertNotSame(indentOptions, settings.OTHER_INDENT_OPTIONS)
  indentOptions.INDENT_SIZE = 2
  indentOptions.CONTINUATION_INDENT_SIZE = 4
  indentOptions.TAB_SIZE = 2
  settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 3
  val groovySettings = settings.getCommonSettings(GroovyLanguage)
  groovySettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
  groovySettings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
  groovySettings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
  initialized(GroovyFormatterFixture(fixture, settings)) {}
}
