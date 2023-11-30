// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.decompiler.navigation

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.test.TestMetadata

class ResolveToDecompiledByAnalysisApiTest : KotlinLightCodeInsightFixtureTestCase() {
  private val mockLibraryFacility = MockLibraryFacility(
    source = IDEA_TEST_DATA_DIR.resolve("decompiler/navigation/resolveByAnalysisApi/library"),
    attachSources = false
  )

  @TestMetadata("idea/tests/testData/decompiler/navigation/resolveByAnalysisApi/LambdaScope.kt")
  @OptIn(KtAllowAnalysisOnEdt::class)
  fun testLambdaScope() {
    myFixture.configureByFile(fileName())
    val reference = myFixture.getReferenceAtCaretPosition()!!
    allowAnalysisOnEdt {
      analyze(reference.element as KtElement) {
        (reference.element as KtElement).resolveCall()
      }
    }
  }

  override fun setUp() {
    super.setUp()
    mockLibraryFacility.setUp(module)
  }

  override fun tearDown() = runAll(
    ThrowableRunnable { mockLibraryFacility.tearDown(module) },
    ThrowableRunnable { super.tearDown() }
  )
}