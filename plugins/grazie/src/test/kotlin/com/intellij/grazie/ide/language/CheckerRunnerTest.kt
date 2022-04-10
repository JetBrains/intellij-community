package com.intellij.grazie.ide.language

import com.intellij.grazie.text.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.util.*
import kotlin.coroutines.coroutineContext

class CheckerRunnerTest : BasePlatformTestCase() {

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
    ProgressManager.getInstance().runProcess(
      {
        UsefulTestCase.assertThrows(ProcessCanceledException::class.java) {
          CheckerRunner(someText()).run(listOf(checker)) {}
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
    ProgressManager.getInstance().runProcess(
      {
        UsefulTestCase.assertThrows(ProcessCanceledException::class.java) {
          CheckerRunner(someText()).run(listOf(checker)) {}
        }
      }, indicator)
  }

  private fun someText(): TextContent {
    val file = myFixture.configureByText("a.txt", "aaabbbccc")
    return TextExtractor.findTextAt(file, 0, TextContent.TextDomain.ALL)!!
  }
}