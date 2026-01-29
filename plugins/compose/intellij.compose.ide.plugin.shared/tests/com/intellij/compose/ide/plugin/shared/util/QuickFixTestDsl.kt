package com.intellij.compose.ide.plugin.shared.util

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language

@DslMarker
annotation class QuickFixTestDsl

@QuickFixTestDsl
class ExpectFix {
  @Language("kotlin")
  lateinit var after: String
  var caretAnchor: String = ""
  var expectedFunctionName: String? = null
}

@QuickFixTestDsl
class ExpectUnavailable {
  private val _positions = mutableListOf<String>()
  fun at(vararg caretAnchors: String) { _positions.addAll(caretAnchors) }
  internal val positions: List<String> get() = _positions
}

@QuickFixTestDsl
class QuickFixTestBuilder(
  private val fixture: CodeInsightTestFixture,
  private val fixFilterFactory: (expectedFunctionName: String?) -> (IntentionAction) -> Boolean,
) {
  @Language("kotlin")
  lateinit var before: String

  private var fixExpectation: ExpectFix? = null
  private var unavailableExpectation: ExpectUnavailable? = null

  fun expectFix(init: ExpectFix.() -> Unit) {
    fixExpectation = ExpectFix().apply(init)
  }

  fun expectUnavailableFix(init: ExpectUnavailable.() -> Unit) {
    unavailableExpectation = ExpectUnavailable().apply(init)
  }

  internal fun execute() {
    require(fixExpectation != null || unavailableExpectation != null) {
      "Must call expectFix {} or expectUnavailableFix {}"
    }
    unavailableExpectation?.let {
      require(it.positions.isNotEmpty()) { "Must add at least one position to expectUnavailableFix via at(...)" }
    }
    fixture.configureByText("Test.kt", before.trimIndent())

    unavailableExpectation?.positions?.forEach { pos ->
      fixture.assertQuickFixNotAvailable(pos, fixFilterFactory(null))
    }

    fixExpectation?.let { exp ->
      fixture.invokeQuickFix(exp.caretAnchor, fixFilterFactory(exp.expectedFunctionName))
      fixture.checkResult(exp.after.trimIndent())
    }
  }
}

fun testQuickFix(
  fixture: CodeInsightTestFixture,
  fixFilterFactory: (expectedFunctionName: String?) -> (IntentionAction) -> Boolean,
  init: QuickFixTestBuilder.() -> Unit
) {
  QuickFixTestBuilder(fixture, fixFilterFactory).apply(init).execute()
}