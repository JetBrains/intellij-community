package org.jetbrains.completion.full.line.local.generation.model

import org.jetbrains.completion.full.line.local.ModelsFiles
import org.jetbrains.completion.full.line.local.TestExecutionContext
import org.jetbrains.completion.full.line.local.tokenizer.FullLineTokenizer
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.math.abs

internal class HiddenStateCachingModelWrapperTest {
  @ParameterizedTest
  @MethodSource("initStartLogProbs source")
  fun initLastLogProbsCorrelation(
    testId: String, inputIds: Array<IntArray>, cacheWarmupInputIds: Array<IntArray>
  ) {
    val (originalOutput, cachingModelOutput) = getOriginalAndCachingOutputs(inputIds, cacheWarmupInputIds)
    val corr = SpearmansCorrelation().correlation(originalOutput, cachingModelOutput)
    val expectedCorr = 0.9
    assertTrue(corr >= expectedCorr, "Expected correlation: $expectedCorr, got: $corr")
  }

  @ParameterizedTest
  @MethodSource("initStartLogProbs source")
  fun initLastLogProbsMeanDiff(
    testId: String, inputIds: Array<IntArray>, cacheWarmupInputIds: Array<IntArray>
  ) {
    val (originalOutput, cachingModelOutput) = getOriginalAndCachingOutputs(inputIds, cacheWarmupInputIds)
    val meanDiff = originalOutput.zip(cachingModelOutput).map {
      abs(it.first - it.second)
    }.average()
    val expectedMeanDiff = 1.5
    assertTrue(meanDiff <= expectedMeanDiff, "Expected mean diff: $expectedMeanDiff, got: $meanDiff")
  }

  @ParameterizedTest
  @MethodSource("cacheHit source")
  fun cacheHit(inputIds: Array<IntArray>, cacheWarmupInputIds: Array<IntArray>, expectedCacheHit: Boolean) {
    cachingGpt2.initLastLogProbs(cacheWarmupInputIds, TestExecutionContext.default)
    isCacheHit = false
    cachingGpt2.initLastLogProbs(inputIds, TestExecutionContext.default).logProbs
    assertEquals(expectedCacheHit, isCacheHit)
  }

  private fun getOriginalAndCachingOutputs(
    inputIds: Array<IntArray>, cacheWarmupInputIds: Array<IntArray>
  ): Pair<DoubleArray, DoubleArray> {
    cachingGpt2.initLastLogProbs(cacheWarmupInputIds, TestExecutionContext.default)
    val originalOutput = gpt2.initLastLogProbs(inputIds, TestExecutionContext.default).logProbs
    val cachingModelOutput = cachingGpt2.initLastLogProbs(inputIds, TestExecutionContext.default).logProbs
    return Pair(originalOutput[0], cachingModelOutput[0])
  }

  @BeforeEach
  fun resetCache() {
    modelCache.reset()
  }

  companion object {
    private val modelFiles = ModelsFiles.gpt2_py_4L_512_83_q_local
    private var gpt2 = GPT2ModelWrapper(modelFiles.model, modelFiles.config)
    private val bpe = FullLineTokenizer(modelFiles.tokenizer, nThreads = 2)
    private var isCacheHit = false
    private val modelCache = object : HiddenStateCache() {
      override fun onCacheHit(commonPrefixLength: Int) {
        isCacheHit = true
      }
    }
    private val cachingGpt2 =
      HiddenStateCachingModelWrapper(GPT2ModelWrapper(modelFiles.model, modelFiles.config), modelCache)

    @JvmStatic
    fun `initStartLogProbs source`(): Stream<Arguments> {
      return Stream.of(
        Arguments.of(
          "Full prefix, BPE", arrayOf(bpe.encode("def hello_world")), arrayOf(bpe.encode("def "))
        ),
        Arguments.of(
          "Full prefix, random ids", arrayOf(intArrayOf(1, 2, 3, 4, 5)), arrayOf(intArrayOf(1, 2))
        ),
        Arguments.of(
          "Full prefix, one token diff", arrayOf(intArrayOf(1, 2, 3, 4, 5)), arrayOf(intArrayOf(1, 2, 3, 4))
        ),
        Arguments.of(
          "Full prefix, one token match", arrayOf(intArrayOf(1, 2, 3, 4)), arrayOf(intArrayOf(1))
        ),
        Arguments.of(
          "Hidden state crop, BPE", arrayOf(bpe.encode("def ")), arrayOf(bpe.encode("def hello_world"))
        ),
        Arguments.of(
          "Hidden state crop, random ids", arrayOf(intArrayOf(1, 2)), arrayOf(intArrayOf(1, 2, 3, 4, 5))
        ),
        Arguments.of(
          "Hidden state crop, one token diff",
          arrayOf(intArrayOf(1, 2, 3, 4)),
          arrayOf(intArrayOf(1, 2, 3, 4, 5))
        ),
        Arguments.of(
          "Hidden state crop, one token match (No cache hit)",
          arrayOf(intArrayOf(5)),
          arrayOf(intArrayOf(5, 1, 3, 4, 5)),
        ),
        Arguments.of(
          "Hidden state crop, one token partial match",
          arrayOf(intArrayOf(5, 1, 3, 4, 5)),
          arrayOf(intArrayOf(5, 2, 2))
        ),
        Arguments.of(
          "Hidden state crop, partial match",
          arrayOf(intArrayOf(5, 1, 3, 4, 5, 2)),
          arrayOf(intArrayOf(5, 1, 3, 7, 8, 9))
        ),
        Arguments.of(
          "Same context", arrayOf(intArrayOf(5, 2, 2, 3, 5)), arrayOf(intArrayOf(5, 2, 2, 3, 5))
        ),
        Arguments.of(
          "No cache hit", arrayOf(intArrayOf(5, 2, 2)), arrayOf(intArrayOf(1, 2, 3, 4, 5))
        ),
      )
    }

    @JvmStatic
    fun `cacheHit source`(): Stream<Arguments> {
      return Stream.of(
        Arguments.of(arrayOf(intArrayOf(5, 1, 3, 4, 5, 2)), arrayOf(intArrayOf(5, 1, 3, 7, 8, 9)), false), // check
        Arguments.of(arrayOf(intArrayOf(5, 1, 3)), arrayOf(intArrayOf(5, 1, 3, 7, 8, 9)), false), // check
        Arguments.of(arrayOf(intArrayOf(2, 4, 3, 7)), arrayOf(intArrayOf(2, 4)), false), // check
        Arguments.of(arrayOf(intArrayOf(5, 2)), arrayOf(intArrayOf(5, 1, 3, 7, 8, 9)), false), // check
        Arguments.of(arrayOf(intArrayOf(5)), arrayOf(intArrayOf(5, 1, 3, 7, 8, 9)), false),
        Arguments.of(arrayOf(intArrayOf(5, 1, 3, 7, 8, 9)), arrayOf(intArrayOf(5)), false), // check
        Arguments.of(arrayOf(intArrayOf(2, 1, 3, 7, 8, 9)), arrayOf(intArrayOf(3, 7, 8, 9)), false),
      )
    }
  }
}
