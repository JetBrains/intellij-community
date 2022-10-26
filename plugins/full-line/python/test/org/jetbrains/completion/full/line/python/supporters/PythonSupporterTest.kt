package org.jetbrains.completion.full.line.language.supporters

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonFileType
import org.jetbrains.completion.full.line.FilesTest
import org.jetbrains.completion.full.line.python.supporters.PythonSupporter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class PythonSupporterTest : BasePlatformTestCase() {

  fun `test string template`() {
    val file = myFixture.configureByText(PythonFileType.INSTANCE, FilesTest.readFile("supporter/string-formats.py", "python"))
    val supporter = PythonSupporter()
    Regex("[^\r\n]+").findAll(file.text).map {
      Executable {
        if (!it.value.startsWith("#")) {
          val template = supporter.createStringTemplate(file, TextRange(it.range.first, it.range.last + 1))!!
          val result = it.next()!!

          assertEquals(result.value.drop(2), template.string)
        }
      }
    }.let { assertAll(it.toList()) }
  }

  fun `test missing brackets`() {
    val expected = StringBuilder()
    val result = StringBuilder()

    val file = myFixture.configureByText(
      PythonFileType.INSTANCE,
      FilesTest.readFile("supporter/missing-braces.py", "python")
    )
    val supporter = PythonSupporter()

    Regex("[^\r\n]+").findAll(file.text).forEach {
      if (!it.value.startsWith("#")) {
        val suggestion = it.value
        val offset = it.range.last + 1
        val fixed = supporter.getMissingBraces(suggestion, file.findElementAt(offset), offset)
                      ?.joinToString("")
                      ?.let { suggestion + it }
                    ?: suggestion
        result.append(fixed).append('\n')
        expected.append(it.next()!!.value.drop(2)).append('\n')
      }
    }

    assertEquals(expected.toString(), result.toString())
  }

  @ParameterizedTest
  @MethodSource("firstTokenData")
  fun `test first token`(line: String, expectedToken: String?) {
    val supporter = PythonSupporter()
    assertEquals(expectedToken, supporter.getFirstToken(line))
  }

  companion object {
    @JvmStatic
    fun firstTokenData(): Stream<Arguments> =
      Stream.of(
        Arguments.of("app.register_blueprint(api, url_prefix='/v1')", "app"),
        Arguments.of(".register_blueprint(api, url_prefix='/v1')", null),
        Arguments.of("register_blueprint(api, url_prefix='/v1')", "register_blueprint"),
        Arguments.of("_blueprint(api, url_prefix='/v1')", "_blueprint"),
        Arguments.of("(api, url_prefix='/v1')", null),
        Arguments.of("api, url_prefix='/v1')", "api"),
        Arguments.of(", url_prefix='/v1')", null),
        Arguments.of("url_prefix='/v1')", "url_prefix"),
        Arguments.of("='/v1')", null),
        Arguments.of("'/v1')", null),
        Arguments.of("/v1')", null),
        Arguments.of("v1')", "v1"),
        Arguments.of("1')", "1"),
        Arguments.of("')", null),
        Arguments.of("", null)
      )
  }
}
