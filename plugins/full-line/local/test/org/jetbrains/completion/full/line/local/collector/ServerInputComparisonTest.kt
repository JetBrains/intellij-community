package org.jetbrains.completion.full.line.local.suggest.collector

import org.jetbrains.completion.full.line.local.ModelsFiles
import org.jetbrains.completion.full.line.local.generation.generation.FullLineGenerationConfig
import org.jetbrains.completion.full.line.local.generation.model.GPT2ModelWrapper
import org.jetbrains.completion.full.line.local.tokenizer.FullLineTokenizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import java.io.File
import java.util.stream.Stream

internal class ServerInputComparisonTest {
  @ParameterizedTest
  @MethodSource("preprocessingTests")
  fun `context match test`(context: String, serverPrefix: String, serverModelInput: List<Int>) {
    val (filename, newContext) = context.split("\n", limit = 2)
    val generationConfig = FullLineGenerationConfig(filename = filename)
    val (contextIds, _) = mockedCompletionsGenerator.makeCompletionInput(newContext, generationConfig)
    assertEquals(serverModelInput, contextIds.toList())
  }

  @ParameterizedTest
  @MethodSource("preprocessingTests")
  fun `prefix match test`(context: String, serverPrefix: String, serverModelInput: List<Int>) {
    val (filename, newContext) = context.split("\n", limit = 2)
    val generationConfig = FullLineGenerationConfig(filename = filename)
    val (_, prefix) = mockedCompletionsGenerator.makeCompletionInput(newContext, generationConfig)
    assertEquals(serverPrefix, prefix)
  }

  @ParameterizedTest
  @MethodSource("preprocessingTests")
  fun `decoded input match`(context: String, serverPrefix: String, serverModelInput: List<Int>) {
    val (filename, newContext) = context.split("\n", limit = 2)
    val generationConfig = FullLineGenerationConfig(filename = filename)
    val (contextIds, _) = mockedCompletionsGenerator.makeCompletionInput(newContext, generationConfig)
    assertEquals(tokenizer.decode(serverModelInput), tokenizer.decode(contextIds))
  }

  companion object {
    private val tokenizer = FullLineTokenizer(ModelsFiles.gpt2_py_4L_512_83_q_local.tokenizer, nThreads = 2)
    private val mockModel = Mockito.mock(GPT2ModelWrapper::class.java)
    private val mockedCompletionsGenerator: FullLineCompletionsGenerator

    init {
      Mockito.`when`(mockModel.maxSeqLen).thenReturn(384)
      mockedCompletionsGenerator = FullLineCompletionsGenerator(mockModel, tokenizer)
    }

    @JvmStatic
    fun preprocessingTests(): Stream<Arguments> {
      val resourceDirName = "flcc/preprocessing"
      fun getResourceFile(name: String): File {
        return File(this::class.java.classLoader.getResource(name)?.file ?: error("Can't find resource: $name"))
      }
      return getResourceFile(resourceDirName).list()!!.map {
        Arguments.of(
          getResourceFile("$resourceDirName/${it}/context.txt").readText(),
          getResourceFile("$resourceDirName/${it}/prefix.txt").readText(),
          getResourceFile("$resourceDirName/${it}/input.txt").readText().split(",").map(String::toInt)
        )
      }.stream()
    }
  }
}