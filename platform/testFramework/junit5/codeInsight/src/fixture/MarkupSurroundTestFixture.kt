// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.fixture

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.impl.InvokeTemplateAction
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.annotations.TestOnly

/**
 * Surround-with test helpers for a markup file in a JUnit 5 test. Create the fixture with [markupSurroundFixture].
 *
 * The test data files are in [BASE_PATH] of the test data path of the code insight fixture.
 * Call the helpers from any thread. They apply the template on the EDT.
 */
@TestOnly
class MarkupSurroundTestFixture internal constructor(
  private val codeInsightFixture: CodeInsightTestFixture,
  /**
   * The name of the current test method without the `test` prefix. The first letter keeps its case.
   */
  val testName: String,
) {
  /**
   * Configures the file `tag/<testName>.<ext>`, applies the first surround template, and checks the result with `tag/<testName>_after.<ext>`.
   */
  fun doSurroundWithTagTest(ext: String) {
    doSurroundTest(getBaseName("tag"), ext, 0)
  }

  /**
   * Configures the file `<testName>.<ext>`, applies the second surround template, and checks the result with `<testName>_after.<ext>`.
   */
  fun doSurroundWithCDataTest(ext: String) {
    doSurroundTest(getBaseName(""), ext, 1)
  }

  /**
   * Returns the path of the test data files for the current test without the extension. The path is relative to the test data path.
   * When [dir] is not empty, the path is in the subdirectory [dir] of [BASE_PATH].
   */
  fun getBaseName(dir: String): String {
    val subDir = if (dir.isEmpty()) "" else "$dir/"
    return "$BASE_PATH$subDir$testName"
  }

  private fun doSurroundTest(baseName: String, ext: String, templateIndex: Int) {
    codeInsightFixture.configureByFile("$baseName.$ext")
    runInEdtAndWait {
      buildSurrounders()[templateIndex].perform()
    }
    codeInsightFixture.checkResultByFile("${baseName}_after.$ext")
  }

  private fun buildSurrounders(): List<InvokeTemplateAction> {
    val editor = codeInsightFixture.editor
    val context = TemplateActionContext.surrounding(codeInsightFixture.file, editor)
    return TemplateManagerImpl.listApplicableTemplateWithInsertingDummyIdentifier(context).map {
      InvokeTemplateAction(it, editor, codeInsightFixture.project, HashSet())
    }
  }

  companion object {
    const val BASE_PATH: String = "/codeInsight/surroundWith/"
  }
}

/**
 * Creates a [MarkupSurroundTestFixture] for the file of [codeInsightFixture].
 *
 * The fixture takes the name of the current test method, so declare it as an instance property.
 */
@TestOnly
fun markupSurroundFixture(codeInsightFixture: TestFixture<CodeInsightTestFixture>): TestFixture<MarkupSurroundTestFixture> =
  testFixture("markupSurroundFixture") { context ->
    val testMethod = context.extensionContext.testMethod.orElseThrow {
      IllegalStateException("markupSurroundFixture needs a test method. Declare it as an instance property.")
    }
    val fixture = MarkupSurroundTestFixture(codeInsightFixture.init(), PlatformTestUtil.getTestName(testMethod.name, false))
    initialized(fixture) {}
  }
