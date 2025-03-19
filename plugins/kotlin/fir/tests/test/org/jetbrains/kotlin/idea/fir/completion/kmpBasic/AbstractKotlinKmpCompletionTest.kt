// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion.kmpBasic

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.k2FileName
import org.jetbrains.kotlin.idea.completion.test.KotlinFixtureCompletionBaseTestCase
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.test.kmp.KMPProjectDescriptorTestUtilities
import org.jetbrains.kotlin.idea.test.kmp.KMPTest
import org.jetbrains.kotlin.platform.TargetPlatform

abstract class AbstractKotlinKmpCompletionTest : KotlinFixtureCompletionBaseTestCase(), KMPTest {

  override val captureExceptions: Boolean = false

  override fun fileName(): String = k2FileName(super.fileName(), testDataDirectory, k2Extension = IgnoreTests.FileExtension.FIR)

  override fun executeTest(test: () -> Unit) {
    IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_K2) {
      super.executeTest(test)
      IgnoreTests.cleanUpIdenticalK2TestFile(dataFile(), k2Extension = IgnoreTests.FileExtension.FIR)
    }
  }

  override fun tearDown() {
    runAll(
      { project.invalidateCaches() },
      { KotlinSdkType.removeKotlinSdkInTests() },
      { super.tearDown() },
    )
  }

  override fun getPlatform(): TargetPlatform = testPlatform.targetPlatform

  override fun defaultCompletionType(): CompletionType {
    return CompletionType.BASIC
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return KMPProjectDescriptorTestUtilities.createKMPProjectDescriptor(testPlatform)
           ?: error("Unsupported platform $testPlatform")
  }
}