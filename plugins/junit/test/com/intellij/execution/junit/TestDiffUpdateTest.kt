// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit

import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.actions.TestDiffRequestProcessor
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.ListSelection
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.ArrayUtilRt
import com.intellij.util.asSafely

abstract class TestDiffUpdateTest : JavaCodeInsightFixtureTestCase() {
  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("junit4", *ArrayUtilRt.toStringArray(JavaSdkUtil.getJUnit4JarPaths()))
  }

  protected open fun checkAcceptDiff(
    before: String,
    after: String,
    testClass: String,
    testName: String,
    expected: String,
    actual: String,
    stackTrace: String,
    fileExt: String
  ) {
    myFixture.configureByText("$testClass.$fileExt", before)
    val testProxy = SMTestProxy(testName, false, "java:test://$testClass/$testName").apply {
      locator = JavaTestLocator.INSTANCE
      setTestFailed("fail", stackTrace, true)
    }
    val hyperlink = testProxy.createHyperlink(expected, actual, null, null, true)
    val requestProducer = TestDiffRequestProcessor.createRequestChain(
      myFixture.project, ListSelection.createSingleton(hyperlink)
    ).requests.first()
    val request = requestProducer.process(UserDataHolderBase(), EmptyProgressIndicator()) as SimpleDiffRequest
    request.onAssigned(true)
    val document = request.contents.firstOrNull().asSafely<DocumentContent>()?.document!!
    document.setReadOnly(false)
    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable { document.replaceString(0, 0, actual) })
    assertEquals(after, myFixture.file.text)
  }
}