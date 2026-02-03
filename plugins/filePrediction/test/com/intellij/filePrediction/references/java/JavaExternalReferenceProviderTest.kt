// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.references.java

import com.intellij.filePrediction.FilePredictionTestDataHelper
import com.intellij.filePrediction.FilePredictionTestDataHelper.DEFAULT_MAIN_FILE
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.containers.ContainerUtil

class JavaExternalReferenceProviderTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath(): String {
    return "${FilePredictionTestDataHelper.defaultTestData}/java/referencesProvider"
  }

  private fun doTest(vararg expected: String) {
    val root = myFixture.copyDirectoryToProject(getTestName(true), "")
    assertNotNull("Cannot create test project", root)

    val file = FilePredictionTestDataHelper.findMainTestFile(root)
    assertNotNull("Cannot find file with '$DEFAULT_MAIN_FILE' name", file)

    val psiFile = PsiManager.getInstance(myFixture.project).findFile(file!!)
    val result = JavaFileReferenceProvider().externalReferences(psiFile!!)
    assertNotNull("Cannot evaluate references for a file", result)

    val actual = result!!.references.map { FileUtil.getRelativePath(root.path, it.path, '/') }.toSet()
    assertEquals(ContainerUtil.newHashSet(*expected), actual)
  }

  fun testGlobalReference() {
    doTest("Baz.java")
  }

  fun testClassReference() {
    doTest("com/test/ui/Baz.java")
  }

  fun testMultipleReferences() {
    doTest("com/test/Helper.java", "com/test/ui/Baz.java", "com/test/component/Foo.java")
  }
}