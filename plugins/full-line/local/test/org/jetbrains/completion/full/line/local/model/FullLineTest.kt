package org.jetbrains.completion.full.line.local.model

import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.completion.full.line.local.CompletionModelFactory
import org.jetbrains.completion.full.line.local.ModelsFiles
import org.jetbrains.completion.full.line.local.TestExecutionContext
import org.jetbrains.completion.full.line.local.pipeline.FullLineCompletionPipelineConfig
import org.junit.jupiter.api.Test

class FullLineTest : UsefulTestCase() {
  companion object {
    private val modelFiles = ModelsFiles.gpt2_py_4L_512_83_q_local
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
}
