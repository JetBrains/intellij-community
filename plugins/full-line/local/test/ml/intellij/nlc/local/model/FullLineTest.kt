package ml.intellij.nlc.local.model

import ml.intellij.nlc.local.CompletionModelFactory
import ml.intellij.nlc.local.ModelsFiles
import ml.intellij.nlc.local.TestExecutionContext
import ml.intellij.nlc.local.pipeline.FullLineCompletionPipelineConfig
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