package org.jetbrains.completion.full.line.js.supporters

import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.completion.full.line.FilesTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class JavaScriptSupporterTest : BasePlatformTestCase() {

  fun `test string template`() {
    val file = myFixture.configureByText(JavaScriptFileType.INSTANCE, FilesTest.readFile("supporter/string-formats.js", "js"))
    val supporter = JSSupporter()
    Regex("[^\\r\\n]+").findAll(file.text).map {
      Executable {
        if (!it.value.startsWith("//")) {
          // TODO: add backticks support
          val template = supporter.createStringTemplate(file, TextRange(it.range.first, it.range.last + 1))!!
          val result = it.next()!!

          assertEquals(result.value.drop(3), template.string)
        }
      }
    }.let { assertAll(it.toList()) }
  }

  fun `test multiline string template`() {
    // TODO: probably, it is useless test, because full line cannot generate multiline strings...
    val file = myFixture.configureByText(JavaScriptFileType.INSTANCE, FilesTest.readFile("supporter/multiline-string.js", "js"))
    val supporter = JSSupporter()
    val template = supporter.createStringTemplate(file, TextRange(0, file.textLength))!!
    val result = "const longString = \"\$__Variable0\$\" + \"\$__Variable1\$\"\n"

    assertEquals(result, template.string)
  }

  @ParameterizedTest
  @MethodSource("firstTokenData")
  fun `test first token`(line: String, expectedToken: String?) {
    val supporter = JSSupporter()
    assertEquals(expectedToken, supporter.getFirstToken(line))
  }


  @Suppress("unused")
  companion object {
    @JvmStatic
    fun firstTokenData(): Stream<Arguments> =
      Stream.of(
        Arguments.of("const stripped = some_exp.replace(stripStringRE, '/v1')", "const"),
        Arguments.of("stripped = some_exp.replace(stripStringRE, '/v1')", "stripped"),
        Arguments.of(" = some_exp.replace(stripStringRE, '/v1')", null),
        Arguments.of("= some_exp.replace(stripStringRE, '/v1')", null),
        Arguments.of("some_exp.replace(stripStringRE, '/v1')", "some_exp"),
        Arguments.of("_exp.replace(stripStringRE, '/v1')", "_exp"),
        Arguments.of(".replace(stripStringRE, '/v1')", null),
        Arguments.of("replace(stripStringRE, '/v1')", "replace"),
        Arguments.of("(stripStringRE, '/v1')", null),
        Arguments.of("stripStringRE, '/v1')", "stripStringRE"),
        Arguments.of(", '/v1')", null),
        Arguments.of("'/v1')", null),
        Arguments.of("/v1')", null),
        Arguments.of("v1')", "v1"),
        Arguments.of("1')", "1"),
        Arguments.of("')", null),
        Arguments.of("", null)
      )
  }
}
