package org.jetbrains.completion.full.line.java.supporters

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.completion.full.line.FilesTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class JavaSupporterTest : BasePlatformTestCase() {

  fun `test string template`() {
    val file = myFixture.configureByText(JavaFileType.INSTANCE, FilesTest.readFile("supporter/StringFormats.java", "java"))
    val supporter = JavaSupporter()
    Regex("[^\r\n]+").findAll(file.text).map {
      Executable {
        if (!it.value.startsWith("//")) {
          val template = supporter.createStringTemplate(file, TextRange(it.range.first, it.range.last + 1))!!
          val result = it.next()!!

          assertEquals(result.value.drop(3), template.string)
        }
      }
    }.let { assertAll(it.toList()) }
  }

  @ParameterizedTest
  @MethodSource("firstTokenData")
  fun `test first token`(line: String, expectedToken: String?) {
    val supporter = JavaSupporter()
    assertEquals(expectedToken, supporter.getFirstToken(line))
  }


  @Suppress("unused")
  companion object {
    @JvmStatic
    fun firstTokenData(): Stream<Arguments> =
      Stream.of(
        Arguments.of("System.out.println(\"Number of digits: \" + count);", "System"),
        Arguments.of(".out.println(\"Number of digits: \" + count);", null),
        Arguments.of("out._println(\"Number of digits: \" + count);", "out"),
        Arguments.of("_println(\"Number of digits: \" + count);", "_println"),
        Arguments.of("(\"Number of digits: \" + count);", null),
        Arguments.of("\"Number of digits: \" + count);", null),
        Arguments.of("Number of digits: \" + count);", "Number"),
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
