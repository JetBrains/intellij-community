package org.jetbrains.completion.full.line.local.suggest.collector

import org.jetbrains.completion.full.line.local.ModelsFiles
import org.jetbrains.completion.full.line.local.generation.model.GPT2ModelWrapper
import org.jetbrains.completion.full.line.local.tokenizer.FullLineTokenizer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito

class IncompleteContextTest {
  @Test
  fun `incomplete context - single token parts`() {
    for (entry in tokenizer.vocab) {
      if (!specialTokenIds.contains(entry.value) && (entry.key.length < mockedCompletionsGenerator.tokenLengthThreshold)) {
        val (context, _) = mockedCompletionsGenerator.resolveIncompleteContext(
          entry.key.substring(0, entry.key.length - 1)
        )
        Assertions.assertEquals("", context, "Wrong on token ${entry.value}: >>>${entry.key}<<<")
      }
    }
  }

  @ParameterizedTest
  @CsvSource("cur_node = TrieNo,cur_node = Trie", "value_sou,value", "df.app,df.", "adfasb.app,adfasb")
  fun `incomplete context - markers`(context: String, expected: String) {
    val (prepContext, _) = mockedCompletionsGenerator.resolveIncompleteContext(context)
    Assertions.assertEquals(expected, prepContext)
  }

  companion object {
    private val tokenizer = FullLineTokenizer(ModelsFiles.gpt2_py_4L_512_793_v3_q_local.tokenizer)
    private val mockModel = Mockito.mock(GPT2ModelWrapper::class.java)
    private val mockedCompletionsGenerator: FullLineCompletionsGenerator
    private val specialTokenIds = setOf(0, 1, 2, 3)

    init {
      Mockito.`when`(mockModel.maxSeqLen).thenReturn(384)
      mockedCompletionsGenerator = FullLineCompletionsGenerator(mockModel, tokenizer)
    }
  }
}
