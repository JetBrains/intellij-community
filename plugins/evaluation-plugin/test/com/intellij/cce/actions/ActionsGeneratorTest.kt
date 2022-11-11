package com.intellij.cce.actions

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.cce.core.*
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.filter.impl.StaticFilter
import com.intellij.cce.filter.impl.StaticFilterConfiguration
import com.intellij.cce.filter.impl.TypeFilter
import com.intellij.cce.filter.impl.TypeFilterConfiguration
import com.intellij.openapi.application.PluginPathManager
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.io.FileReader

class ActionsGeneratorTest {
  companion object {
    private val GSON = GsonBuilder().setPrettyPrinting().create()
  }

  private val file = Mockito.mock(CodeFragment::class.java)

  init {
    val fileText = """
            public class Example {
                public static void main(String[] args) {
                    test(args.length);
                }
                private static void test(Object a) {
                }
            }
        """.trimIndent()

    Mockito.`when`(file.text).thenReturn(fileText)
    Mockito.`when`(file.offset).thenReturn(0)
    Mockito.`when`(file.length).thenReturn(fileText.length)
    Mockito.`when`(file.path).thenReturn("example.java")
    Mockito.`when`(file.getChildren()).thenReturn(listOf(
      CodeToken("public", 0, 6, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {}),
      CodeToken("class", 7, 5, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {}),
      CodeToken("public", 27, 6, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {}),
      CodeToken("static", 34, 6, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {}),
      CodeToken("void", 41, 4, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {}),
      CodeToken("String", 51, 6, JvmProperties.create(TypeProperty.TYPE_REFERENCE, SymbolLocation.LIBRARY) {}),
      CodeToken("test", 76, 4, JvmProperties.create(TypeProperty.METHOD_CALL, SymbolLocation.PROJECT) { isStatic = true }),
      CodeToken("args", 81, 4, JvmProperties.create(TypeProperty.VARIABLE, SymbolLocation.PROJECT) {}),
      CodeToken("length", 86, 6, JvmProperties.create(TypeProperty.FIELD, SymbolLocation.LIBRARY) { isStatic = false }),
      CodeToken("private", 105, 7, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {}),
      CodeToken("static", 113, 6, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {}),
      CodeToken("void", 120, 4, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {}),
      CodeToken("Object", 130, 6, JvmProperties.create(TypeProperty.TYPE_REFERENCE, SymbolLocation.LIBRARY) {}),
    ))
  }

  private val testData = PluginPathManager.getPluginHome("evaluation-plugin").resolve("testData/actions")

  @Test
  fun `test default strategy`() = doTest(outputFile = "default.json")

  @Test
  fun `test empty prefix`() = doTest(outputFile = "no-prefix.json", prefix = CompletionPrefix.NoPrefix)

  @Test
  fun `test capitalized prefix`() = doTest(outputFile = "capitalize-prefix.json",
                                           prefix = CompletionPrefix.CapitalizePrefix(emulateTyping = false))

  @Test
  fun `test emulation typing`() = doTest(outputFile = "emulate-typing.json",
                                         prefix = CompletionPrefix.SimplePrefix(n = 3, emulateTyping = true))

  @Test
  fun `test previous context`() = doTest(outputFile = "previous-context.json", context = CompletionContext.PREVIOUS)

  @Test
  fun `test keywords filter`() = doTest(outputFile = "keywords-filter.json", filters = mapOf(
    TypeFilterConfiguration.id to TypeFilter(listOf(TypeProperty.KEYWORD))
  ))

  @Test
  fun `test static methods filter`() = doTest(outputFile = "static-methods-filter.json", filters = mapOf(
    TypeFilterConfiguration.id to TypeFilter(listOf(TypeProperty.METHOD_CALL)),
    StaticFilterConfiguration.id to StaticFilter(true)
  ))

  @Test
  fun `test completion golf`() = doTest(outputFile = "completion-golf.json", completionGolf = true)

  private fun doTest(outputFile: String,
                     prefix: CompletionPrefix = CompletionPrefix.SimplePrefix(emulateTyping = false, n = 1),
                     context: CompletionContext = CompletionContext.ALL,
                     emulateUser: Boolean = false,
                     completionGolf: Boolean = false,
                     filters: Map<String, EvaluationFilter> = emptyMap()) {
    val actionsGenerator = ActionsGenerator(CompletionStrategy(prefix, context, emulateUser, completionGolf, filters), Language.JAVA)
    val actions = actionsGenerator.generate(file)
    val actual = ActionSerializer.serialize(actions).prettifyJson().trim()
    val expected = FileReader(testData.resolve(outputFile)).use { it.readText() }.trim()
    Assertions.assertEquals(expected, actual)
  }

  private fun String.prettifyJson() = GSON.toJson(JsonParser.parseString(this))
}
