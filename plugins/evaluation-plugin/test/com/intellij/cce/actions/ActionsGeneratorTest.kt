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
      CodeLine("public", 0).apply { addChild(CodeToken(text, offset, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {})) },
      CodeLine("class", 7).apply {  addChild(CodeToken(text, offset, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {})) },
      CodeLine("public", 27).apply {  addChild(CodeToken(text, offset, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {})) },
      CodeLine("static", 34).apply {  addChild(CodeToken(text, offset, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {})) },
      CodeLine("void", 41).apply {  addChild(CodeToken(text, offset, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {})) },
      CodeLine("String", 51).apply {  addChild(CodeToken(text, offset, JvmProperties.create(TypeProperty.TYPE_REFERENCE, SymbolLocation.LIBRARY) {})) },
      CodeLine("test", 76).apply {  addChild(CodeToken(text, offset, JvmProperties.create(TypeProperty.METHOD_CALL, SymbolLocation.PROJECT) { isStatic = true })) },
      CodeLine("args", 81).apply {  addChild(CodeToken(text, offset, JvmProperties.create(TypeProperty.VARIABLE, SymbolLocation.PROJECT) {})) },
      CodeLine("length", 86).apply {  addChild(CodeToken(text, offset, JvmProperties.create(TypeProperty.FIELD, SymbolLocation.LIBRARY) { isStatic = false })) },
      CodeLine("private", 105).apply {  addChild(CodeToken(text, offset, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {})) },
      CodeLine("static", 113).apply {  addChild(CodeToken(text, offset, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {})) },
      CodeLine("void", 120).apply {  addChild(CodeToken(text, offset, JvmProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {})) },
      CodeLine("Object", 130).apply {  addChild(CodeToken(text, offset, JvmProperties.create(TypeProperty.TYPE_REFERENCE, SymbolLocation.LIBRARY) {})) },
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
  fun `test completion golf`() = doTest(outputFile = "completion-golf.json", completionGolf = CompletionGolfMode.ALL)

  private fun doTest(outputFile: String,
                     prefix: CompletionPrefix = CompletionPrefix.SimplePrefix(emulateTyping = false, n = 1),
                     context: CompletionContext = CompletionContext.ALL,
                     emulateUser: Boolean = false,
                     completionGolf: CompletionGolfMode? = null,
                     filters: Map<String, EvaluationFilter> = emptyMap()) {
    val actionsGenerator = ActionsGenerator(CompletionStrategy(prefix, context, emulateUser, completionGolf, filters), Language.JAVA)
    val actions = actionsGenerator.generate(file)
    val actual = ActionSerializer.serialize(actions).prettifyJson().trim()
    val expected = FileReader(testData.resolve(outputFile)).use { it.readText() }.trim()
    Assertions.assertEquals(expected, actual)
  }

  private fun String.prettifyJson() = GSON.toJson(JsonParser.parseString(this))
}
