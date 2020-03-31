// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.java

import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.containers.ContainerUtil

class JavaExternalReferenceProviderTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getTestDataPath(): String {
    return "$defaultTestData/java/referencesProvider"
  }

  private fun doTest(vararg expected: String) {
    val root = myFixture.copyDirectoryToProject(getTestName(true), "")
    assertNotNull("Cannot create test project", root)

    val file = findChildRecursively(root)
    assertNotNull("Cannot find file with '$DEFAULT_MAIN_FILE' name", file)

    val psiFile = PsiManager.getInstance(myFixture.project).findFile(file!!)
    val result = JavaFileReferenceProvider().externalReferences(psiFile!!)
    assertNotNull("Cannot evaluate references for a file", result)

    val actual = result!!.references.map { FileUtil.getRelativePath(root.path, it.path,'/') }.toSet()
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

  companion object {
    private const val DEFAULT_MAIN_FILE = "MainTest"
    private val defaultTestData: String = PlatformTestUtil.getCommunityPath() + "/plugins/filePrediction/testData/com/intellij/filePrediction"

    private fun findChildRecursively(root: VirtualFile): VirtualFile? {
      val target = Ref<VirtualFile>()
      VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any?>() {
        override fun visitFile(file: VirtualFile): Boolean {
          val isMainTestFile = FileUtil.namesEqual(FileUtil.getNameWithoutExtension(file.name), DEFAULT_MAIN_FILE)

          if (isMainTestFile && target.isNull) {
            target.set(file)
          }
          return !isMainTestFile
        }
      })
      return target.get()
    }
  }
}