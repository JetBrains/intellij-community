package org.jetbrains.completion.full.line.local.model

import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.completion.full.line.local.CompletionModelFactory
import org.jetbrains.completion.full.line.local.ModelsFiles
import org.jetbrains.completion.full.line.local.TestExecutionContext
import org.jetbrains.completion.full.line.local.pipeline.FullLineCompletionPipelineConfig
import org.junit.jupiter.api.Test

class FullLineTest : UsefulTestCase() {
  companion object {
    private val modelFiles = ModelsFiles.currentModel
    val context = """
            def hello_
        """.trimIndent()
    const val prefix = "hello_"
  }

  @Test
  fun errorTest() = assertNoException<RuntimeException>(AssertionError::class.java) {
    val completionModel = CompletionModelFactory.createFullLineCompletionModel(
      modelFiles.tokenizer,
      modelFiles.model,
      modelFiles.config
    )

    val completions = completionModel.generateCompletions(
      context, prefix, FullLineCompletionPipelineConfig(
      maxLen = 8,
      filename = "hello_world.py"
    ), TestExecutionContext.default
    )

    print(completions.map { it.text })
  }

  fun `test simple python completion`() {
    val completionModel = CompletionModelFactory.createFullLineCompletionModel(
      modelFiles.tokenizer,
      modelFiles.model,
      modelFiles.config
    )
    val content = """
      if 
    """.trimIndent()
    val prefix = ""

    val completions = completionModel.generateCompletions(
      content, prefix,
      FullLineCompletionPipelineConfig(maxLen = 8, filename = "main.py"),
      TestExecutionContext.default
    )

    assertContainsElements(completions.map { it.text }, "__name__ == \"__main__\":")
  }

  fun `test long token python completion failure`() {
    val completionModel = CompletionModelFactory.createFullLineCompletionModel(
      modelFiles.tokenizer,
      modelFiles.model,
      modelFiles.config
    )
    val content = """from src.some_classes import SomeClass
def modify_state(obj: SomeClass):
    obj.super_long_counter_name_1 += 1
    obj.super_long_counter_name_2 += 1
    obj.super_long_counter_name_3 += 1
    obj.super_long_counter_name_4 += 1
    obj."""
    val prefix = ""

    val completions = completionModel.generateCompletions(
      content, prefix,
      FullLineCompletionPipelineConfig(maxLen = 2, filename = "main.py"),
      TestExecutionContext.default
    )

    assertEmpty(completions.filter { it.text.trim().isNotEmpty() && it.info.probs.reduce(Double::times) > 0.1 })
  }
}
