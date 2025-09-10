package com.intellij.grazie.ide.language

import com.intellij.grazie.text.*
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.TimeoutUtil
import com.intellij.util.application
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class CheckerRunnerTest: BasePlatformTestCase() {
  fun `test async cancellability`() {
    val indicator = ProgressIndicatorBase()
    val checker = object: ExternalTextChecker() {
      override suspend fun checkExternally(content: TextContent): Collection<TextProblem> {
        indicator.cancel()

        repeat(10) {
          coroutineContext.ensureActive()
          delay(10)
        }

        fail("We should be already canceled")
        return emptyList()
      }
      override fun getRules(locale: Locale) = throw UnsupportedOperationException()
    }
    maskChecker(checker)
    ProgressManager.getInstance().runProcess(
      {
        assertThrows(ProcessCanceledException::class.java) {
          CheckerRunner(someText()).run()
        }
      }, indicator)
  }

  fun `test sync cancellability`() {
    val indicator = ProgressIndicatorBase()
    val checker = object: TextChecker() {
      override fun check(extracted: TextContent): MutableCollection<out TextProblem> {
        indicator.cancel()

        repeat(10) {
          ProgressManager.checkCanceled()
          TimeoutUtil.sleep(10)
        }

        fail("We should be already canceled")
        return ArrayList()
      }

      override fun getRules(locale: Locale) = throw UnsupportedOperationException()
    }
    maskChecker(checker)
    ProgressManager.getInstance().runProcess(
      {
        assertThrows(ProcessCanceledException::class.java) {
          CheckerRunner(someText()).run()
        }
      }, indicator)
  }


  fun `test cancellability in problem processing`() {
    // Create a single line huge text without spaces, so we can really make those regexes work
    val text = makeHugeSingleLineText()
    val indicator = ProgressIndicatorBase()
    val latch = CountDownLatch(1)
    val checker = object: TextChecker() {
      override fun getRules(locale: Locale): MutableCollection<out Rule> {
        throw UnsupportedOperationException()
      }

      override fun check(extracted: TextContent): MutableCollection<out TextProblem> {
        if (extracted.isBlank()) {
          return mutableListOf()
        }
        val problem = createFakeProblem(extracted, TextRange(0, extracted.length))
        latch.countDown()
        return mutableListOf(problem)
      }
    }
    maskChecker(checker)
    val collectedProblems = ArrayList<TextProblem>()
    val result = application.executeOnPooledThread(Callable {
      ProgressManager.getInstance().runProcess(Computable {
        runReadAction {
          collectedProblems.addAll(CheckerRunner(text).run())
        }
      }, indicator)
    })
    latch.await(5, TimeUnit.SECONDS)
    TimeoutUtil.sleep(50)
    indicator.cancel()
    result.get()
    // There should be no problems collected, since the processing should've been canceled
    assertTrue(collectedProblems.isEmpty())
  }

  private fun createFakeProblem(text: TextContent, range: TextRange): TextProblem {
    val rule = object: Rule("fake.global.id", "Fake rule", "Fake category") {
      override fun getDescription(): String {
        return "Fake rule description"
      }
    }
    return object: TextProblem(rule, text, range) {
      override fun getShortMessage(): String {
        return "Fake problem short message"
      }

      override fun getDescriptionTemplate(isOnTheFly: Boolean): String {
        return "Fake problem description template"
      }
    }
  }

  private fun makeHugeSingleLineText(): TextContent {
    val content = buildString {
      repeat(100000) {
        append("aaabbbcccddd")
      }
      append(".\n")
    }
    val file = myFixture.configureByText("huge-file.txt", content)
    return TextExtractor.findTextAt(file, 0, TextContent.TextDomain.ALL)!!
  }

  private fun someText(): TextContent {
    val file = myFixture.configureByText("a.txt", "aaabbbccc")
    return TextExtractor.findTextAt(file, 0, TextContent.TextDomain.ALL)!!
  }

  private fun maskChecker(checker: TextChecker) {
    ExtensionTestUtil.maskExtensions(
      ExtensionPointName("com.intellij.grazie.textChecker"),
      listOf(checker),
      testRootDisposable
    )
  }
}