package org.jetbrains.completion.full.line.kotlin.supporters

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.completion.full.line.FilesTest
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class KotlinSupporterTest : BasePlatformTestCase() {

  fun `test string template`() {
    val file = myFixture.configureByText(KotlinFileType.INSTANCE, FilesTest.readFile("supporter/StringFormats.kt", "kotlin"))
    val supporter = KotlinSupporter()

    val amountOfLinesToSkip = 1

    Regex("[^\r\n]+").findAll(file.text).mapIndexedNotNull { i, it ->
      if (i < amountOfLinesToSkip) {
        return@mapIndexedNotNull null
      }
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
    val supporter = KotlinSupporter()
    assertEquals(expectedToken, supporter.getFirstToken(line))
  }


  @Suppress("unused")
  companion object {
    @JvmStatic
    fun firstTokenData(): Stream<Arguments> =
      Stream.of(
        Arguments.of("if (!it.value.startsWith(\"//\")) {", "if"),
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
//val test2 = "test2" + "abracadabra" + "string" + "\$__Variable10$" + "\n"
//// val test2 = "$__Variable0$" + "$__Variable1$" + "$__Variable2$" + "$__Variable3$" + "$__Variable4$"
//val test3 = "all kinds of spaces\n\t\r" + item + "  all  kinds  of  spaces2\n g\t g\r "
//// val test3 = "$__Variable0$" + a_variable + "$__Variable1$"
