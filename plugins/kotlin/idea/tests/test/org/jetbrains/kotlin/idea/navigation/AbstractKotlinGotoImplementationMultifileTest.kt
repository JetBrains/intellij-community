// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.navigation

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import kotlin.io.path.Path

@RunWith(JUnit38ClassRunner::class)
abstract class AbstractKotlinGotoImplementationMultifileTest : KotlinLightCodeInsightFixtureTestCase() {
  protected fun doKotlinJavaTest(path: String) {
      IgnoreTests.runTestIfNotDisabledByFileDirective(Path(path), IgnoreTests.DIRECTIVES.of(pluginMode)) {
          doMultiFileTest(getTestName(false) + ".kt", getTestName(false) + ".java")
      }
  }

  protected fun doJavaKotlinTest(ignored: String) {
    doMultiFileTest(getTestName(false) + ".java", getTestName(false) + ".kt")
  }

  protected override fun getProjectDescriptor(): LightProjectDescriptor {
    return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
  }

  private fun doMultiFileTest(vararg fileNames: String?) {
    myFixture.configureByFiles(*fileNames)
    val gotoData = NavigationTestUtils.invokeGotoImplementations(getEditor(), getFile())
    NavigationTestUtils.assertGotoDataMatching(getEditor(), gotoData)
  }
}
