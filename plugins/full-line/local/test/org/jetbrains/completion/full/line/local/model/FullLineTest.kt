package org.jetbrains.completion.full.line.local.model

import org.jetbrains.completion.full.line.local.CompletionModelFactory
import org.jetbrains.completion.full.line.local.ModelsFiles
import org.jetbrains.completion.full.line.local.TestExecutionContext
import org.jetbrains.completion.full.line.local.pipeline.FullLineCompletionPipelineConfig
import org.junit.jupiter.api.Test

class FullLineTest {
  companion object {
    private val modelFiles = ModelsFiles.gpt2_py_4L_256_78
    val context = """
            def hello_
        """.trimIndent()
    const val prefix = "_"
  }

  @Test
  fun errorTest() {
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
}