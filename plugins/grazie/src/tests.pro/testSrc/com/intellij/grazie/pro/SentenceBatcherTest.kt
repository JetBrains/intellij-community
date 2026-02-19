package com.intellij.grazie.pro

import ai.grazie.nlp.langs.Language
import ai.grazie.rules.util.BatchParser
import ai.grazie.text.exclusions.SentenceWithExclusions
import com.intellij.grazie.rule.SentenceBatcher
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.UsefulTestCase.assertThrows
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.util.TimeoutUtil
import com.intellij.util.application
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.io.awaitFor
import com.jetbrains.rd.util.Callable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

@RunInEdt(allMethods = true, writeIntent = true)
class SentenceBatcherTest : BaseTestCase() {
  private var viewProviderInstance: FileViewProvider? = null
  private var batcherInstance: TestBatcher? = null

  private val viewProvider: FileViewProvider
    get() = checkNotNull(viewProviderInstance)

  private val batcher: TestBatcher
    get() = checkNotNull(batcherInstance)

  @BeforeEach
  fun setUp() {
    viewProviderInstance = createLightFile(PlainTextFileType.INSTANCE, sentences.joinToString(separator = "")).viewProvider
    batcherInstance = TestBatcher()
  }

  @Test
  fun testFirstQueryBatches() {
    val sentence = sentences[10]
    assertEquals(outputs[sentence], parseWithProgress(batcher.forFile(viewProvider), sentence))
    assertEquals(sentences.subList(10, 20), batcher.queries)
  }

  @Test
  fun testSecondQueryDoesNotContainFirstBatch() {
    parseWithProgress(batcher.forFile(viewProvider), sentences[10])
    parseWithProgress(batcher.forFile(viewProvider), sentences[5])
    val expected = sentences.subList(10, 20) + sentences.subList(5, 10) + sentences.subList(20, 25)
    assertEquals(expected, batcher.queries)
  }

  @Test
  fun testLastSentenceHasFullSizeBatch() {
    parseWithProgress(runReadAction { batcher.forFile(viewProvider) }, sentences[sentences.size - 1])
    assertEquals(
      HashSet(sentences.subList(sentences.size - 10, sentences.size)),
      HashSet(batcher.queries)
    )
  }

  @Test
  fun testNoDuplicates() {
    val expectedBatch = listOf(sentences[sentences.lastIndex - 1], sentences.last())
    val smallFile = createLightFile(PlainTextFileType.INSTANCE, expectedBatch.joinToString(separator = "")).viewProvider
    withProgress { runReadAction { batcher.forFile(smallFile).parse(expectedBatch) } }
    assertEquals(expectedBatch, batcher.queries)
  }

  @Test
  fun testMultiThreadedQueriesDoNotIntersect() {
    val shuffled = sentences.shuffled()
    val futures = shuffled.map { sentence ->
      application.executeOnPooledThread(Callable {
        ProgressManager.getInstance().runProcess<String>({
          ReadAction.compute<String, RuntimeException> {
            batcher.forFile(viewProvider).parseSingle(sentence)
          }
        }, EmptyProgressIndicator())
      })
    }
    for (future in futures) {
      future.get()
    }
    assertEquals(outputs.size, batcher.queries.size)
  }

  @RunMethodInEdt(RunMethodInEdt.WriteIntentMode.True)
  @Test
  fun testSupportsNullResult() {
    val batcher = object: SentenceBatcher<String?>(Language.ENGLISH, 10) {
      override suspend fun parse(sentences: List<SentenceWithExclusions>, project: Project): Map<SentenceWithExclusions, String?> {
        val result = LinkedHashMap<SentenceWithExclusions, String?>()
        for (sentence in sentences) {
          result[sentence] = null
        }
        return result
      }
    }
    assertNull(parseWithProgress(runReadAction { batcher.forFile(viewProvider) }, sentences[0]))
  }

  @Test
  fun testCachesResultsDespiteCancellation() {
    val mainSemaphore = Semaphore(1)
    val bgSemaphore = Semaphore(1)
    val indicator = ProgressIndicatorBase()
    val batcher: TestBatcher = object: TestBatcher() {
      override suspend fun parse(sentences: List<SentenceWithExclusions>, project: Project): Map<SentenceWithExclusions, String?> {
        try {
          indicator.cancel()
          mainSemaphore.waitFor()
          return super.parse(sentences, project)
        } finally {
          bgSemaphore.up()
        }
      }
    }
    ProgressManager.getInstance().runProcess({
                                               assertThrows(ProcessCanceledException::class.java) {
                                                 runReadAction {
                                                   batcher.forFile(viewProvider).parseSingle(sentences[0])
                                                 }
                                               }
                                             }, indicator)
    mainSemaphore.up()
    bgSemaphore.waitFor()

    TimeoutUtil.sleep(10) // for the bg thread to complete

    assertEquals(outputs[sentences[1]], parseWithProgress(batcher.forFile(viewProvider), sentences[1]))
    assertSize(BATCH_SIZE, batcher.queries)
  }

  private fun parseWithProgress(parser: SentenceBatcher.AsyncBatchParser<String?>, sentence: String): String? {
    return withProgress { parser.parseSingle(sentence) }
  }

  private fun <T> withProgress(function: () -> T): T =
    ProgressManager.getInstance()
      .runProcessWithProgressSynchronously<T, RuntimeException>(function, "", false, project)

  @Test
  fun testNoParallelRequestsResultsDespiteCancellation() {
    val mainSemaphore = Semaphore(1)
    val indicator = ProgressIndicatorBase()
    val concurrent = AtomicInteger()
    val batcher = object: TestBatcher() {
      override suspend fun parse(sentences: List<SentenceWithExclusions>, project: Project): Map<SentenceWithExclusions, String?> {
        try {
          assertEquals(1, concurrent.incrementAndGet())
          indicator.cancel()
          mainSemaphore.awaitFor()
          return super.parse(sentences, project)
        } finally {
          assertEquals(0, concurrent.decrementAndGet())
        }
      }
    }
    ProgressManager.getInstance().runProcess({
                                               assertThrows(ProcessCanceledException::class.java) {
                                                 runReadAction {
                                                   batcher.forFile(viewProvider) }.parseSingle(sentences[0])
                                               }
                                             }, indicator)
    mainSemaphore.up()

    val middleSentence = sentences[BATCH_SIZE * 3]
    assertEquals(outputs[middleSentence], parseWithProgress(batcher.forFile(viewProvider), middleSentence))
    assertSize(BATCH_SIZE * 2, batcher.queries)
  }

  @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.True)
  @Test
  fun testHandlesEmptyMap() {
    val parser: BatchParser<String> = object: SentenceBatcher<String>(Language.ENGLISH, 10) {
      override suspend fun parse(sentences: List<SentenceWithExclusions>, project: Project): Map<SentenceWithExclusions, String> {
        return emptyMap()
      }
    }.forFile(viewProvider)
    val result = withProgress { parser.parse(listOf("a", "b")) }
    assertEquals(setOf("a", "b"), result.keys)
    assertNull(result["a"])
    assertNull(result["b"])
  }

  @Test
  fun testMinimalParsesOnlyRequestedSentences() {
    parseWithProgress(batcher.minimal(project), sentences[8])
    assertEquals(listOf(sentences[8]), batcher.queries)
  }

  private open class TestBatcher: SentenceBatcher<String?>(Language.ENGLISH, BATCH_SIZE) {
    var queries: MutableList<String?> = Collections.synchronizedList(ArrayList())

    override suspend fun parse(sentences: List<SentenceWithExclusions>, project: Project): Map<SentenceWithExclusions, String?> {
      queries.addAll(sentences.map { it.sentence })
      return sentences.associateWith { outputs[it.sentence] }
    }
  }

  @Suppress("SameParameterValue")
  private fun createLightFile(fileType: FileType, text: String): PsiFile {
    return PsiFileFactory.getInstance(project).createFileFromText("a." + fileType.getDefaultExtension(), fileType, text)
  }

  companion object {
    private val sentences = (0 until 99).map { "This is sentence number $it.\n" } + listOf("This is sentence number 99.")
    private val outputs = sentences.associateWith { it + it }
    private const val BATCH_SIZE = 10
  }
}