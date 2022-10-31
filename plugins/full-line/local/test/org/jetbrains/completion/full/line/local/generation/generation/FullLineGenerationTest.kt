package org.jetbrains.completion.full.line.local.generation.generation

import org.jetbrains.completion.full.line.local.ModelsFiles
import org.jetbrains.completion.full.line.local.TestExecutionContext
import org.jetbrains.completion.full.line.local.generation.model.GPT2ModelWrapper
import org.jetbrains.completion.full.line.local.tokenizer.FullLineTokenizer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.Integer.max
import java.util.*
import kotlin.system.measureTimeMillis

class FullLineGenerationTest {
  companion object {
    private val modelFiles = ModelsFiles.gpt2_py_4L_256_78
    private val gpt2 = GPT2ModelWrapper(modelFiles.model, modelFiles.config)
    private val bpe = FullLineTokenizer(modelFiles.tokenizer, nThreads = 2)
    private val generation = FullLineGeneration(gpt2, bpe)
  }

  @Test
  fun genTest() {
    val context = """
            hello_world.py
            ₣
            def hello_
        """.trimIndent()
    val prefix = "_"
    val contextIds = bpe.encode(context)
    val config = FullLineGenerationConfig(maxLen = 10)
    val result = generation.generate(contextIds, prefix, config, TestExecutionContext.default)
    val variants = result.map { it.map { info -> bpe.decode(info.ids) } }.last()
    val expected = "_world():\n"
    assertTrue(variants.contains(expected), "Variants are expected to contain: \"$expected\"")
  }

  @Test
  fun badTokens() {
    val context = """
            hello_world.py
            ₣
            def hello_world():
            
        """.trimIndent()
    val prefix = "\tp"
    val contextIds = bpe.encode(context)
    val config = FullLineGenerationConfig(maxLen = 8, filename = "hello_world.py")
    val result = generation.generate(contextIds, prefix, config, TestExecutionContext.default)
    val variants = result.map { it.map { info -> bpe.decode(info.ids) } }.last()
    val badTokenRegex = Regex("<PAD>|<BOS>|<EOS>")
    variants.forEach {
      assertFalse(it.contains(badTokenRegex), "Unexpected tokens were met in variant: \"$it\"")
    }
  }

  @Test
  @Disabled
  fun timeTest() {
    val shuffle = false
    val url = Thread.currentThread().contextClassLoader.getResource("time_test/time_test_context.txt")
    val file = File(url?.path ?: error("No resource for time test"))
    val context = file.readText()
    val prefix = ""
    var contextLengths = (2500 downTo 10 step 10).toList()
    if (shuffle) {
      contextLengths = contextLengths.shuffled()
    }
    val contextLenToTime = TreeMap<Int, Long>()
    for (contextEnd in contextLengths) {
      val contextStart = max(contextEnd - generation.model.maxSeqLen, 0)
      val splitContext = context.substring(contextStart, contextEnd)
      contextLenToTime[contextEnd] = measureTimeMillis {
        val contextIds = bpe.encode(splitContext)
        val config = FullLineGenerationConfig(maxLen = 5)
        val result = generation.generate(contextIds, prefix, config, TestExecutionContext.default)
        result.map { it.map { info -> bpe.decode(info.ids) } }.last()
      }
    }
    println(contextLenToTime.map { it.key })
    val times = contextLenToTime.map { it.value }

    println("Average: ${times.average()}")
    println(times)
  }
}
