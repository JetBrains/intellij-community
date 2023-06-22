// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.testFramework.LightProjectDescriptor
import java.nio.charset.Charset
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

class FirCompletionOutsideSourceRootTest : KotlinLightCodeInsightFixtureTestCase() {
  fun testFunctionName() {
      val tempFile = createTempFile("kt", null, "fun <caret>", Charset.defaultCharset())
      myFixture.configureFromExistingVirtualFile(tempFile)
      myFixture.completeBasic()
  }

    override fun isFirPlugin(): Boolean {
        return true
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }
}