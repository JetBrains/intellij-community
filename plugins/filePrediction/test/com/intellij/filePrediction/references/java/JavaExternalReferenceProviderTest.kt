// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.references.java

import com.intellij.filePrediction.FilePredictionTestDataHelper
import com.intellij.filePrediction.FilePredictionTestDataHelper.DEFAULT_MAIN_FILE
import com.intellij.filePrediction.java.JavaFileReferenceProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/filePrediction/testData/com/intellij/filePrediction/java/referencesProvider")
@Disabled("AT-3959")
class JavaExternalReferenceProviderTest {
  private val projectFixture = codeInsightProjectFixture()
  private val myFixture by codeInsightFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true)
  }

  private fun doTest(vararg expected: String): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val root = myFixture.copyDirectoryToProject(testName, "")
      assertNotNull(root, "Cannot create test project")

      val file = FilePredictionTestDataHelper.findMainTestFile(root)
      assertNotNull(file, "Cannot find file with '$DEFAULT_MAIN_FILE' name")

      val psiFile = PsiManager.getInstance(myFixture.project).findFile(file!!)
      val result = JavaFileReferenceProvider().externalReferences(psiFile!!)
      assertNotNull(result, "Cannot evaluate references for a file")

      val actual = result!!.references.map { FileUtil.getRelativePath(root.path, it.path, '/') }.toSet()
      assertEquals(ContainerUtil.newHashSet(*expected), actual)
    }
  }

  @Test
  fun testGlobalReference() {
    doTest("Baz.java")
  }

  @Test
  fun testClassReference() {
    doTest("com/test/ui/Baz.java")
  }

  @Test
  fun testMultipleReferences() {
    doTest("com/test/Helper.java", "com/test/ui/Baz.java", "com/test/component/Foo.java")
  }
}