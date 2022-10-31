package ml.intellij.nlc.local.tokenizer

import ml.intellij.nlc.local.ModelsFiles
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TokenizerTrieTest {
  companion object {
    private val trie = TokenizerTrie(FullLineTokenizer(ModelsFiles.gpt2_py_6L_82_old_data.tokenizer, nThreads = 2))
  }

  @Test
  fun `split token`() {
    val values = trie.getValuesWithPrefix(".app", false)
    assertTrue(values.contains(trie.tokenizer.encode(".").last()))
  }

  @Test
  fun `split token second step`() {
    val values = trie.getValuesWithPrefix("app", false)
    assertTrue(values.contains(trie.tokenizer.encode("apply").last()))
  }

  @Test
  fun `strict prefix matching`() {
    val values = trie.getValuesWithPrefix(".app", true)
    assertFalse(values.contains(trie.tokenizer.encode(".").last()))
  }

  @Test
  fun `empty prefix`() {
    val values = trie.getValuesWithPrefix("\t\t\tvocab_size = ", true)
    assertTrue(values.isEmpty())
  }
}