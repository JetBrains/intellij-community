// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JavadocOrderRootType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.rules.TestNameExtension
import com.intellij.util.ArrayUtilRt
import com.intellij.util.io.copy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.div

@ExperimentalPathApi
class EclipseLibrariesModificationsTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @JvmField
  @RegisterExtension
  val testName = TestNameExtension()

  @Test
  fun testReplacedWithVariables() {
    doTestCreate(arrayOf("/variableidea/test.jar!/"), arrayOf("/srcvariableidea/test.jar!/"))
  }

  @Test
  fun testCantReplaceWithVariables() {
    doTestCreate(arrayOf("/variableidea1/test.jar!/"), arrayOf("/srcvariableidea/test.jar!/"))
  }

  @Test
  fun testReplacedWithVariablesNoSrcExistOnDisc() {
    doTestCreate(arrayOf("/variableidea/test.jar!/"), arrayOf("/srcvariableidea/test.jar!/"))
  }

  @Test
  fun testReplacedWithVariablesCantReplaceSrc() {
    doTestCreate(arrayOf("/variableidea/test.jar!/"), arrayOf("/srcvariableidea1/test.jar!/"))
  }

  @Test
  fun testReplacedWithVariablesNoSources() {
    doTestCreate(arrayOf("/variableidea/test.jar!/"), arrayOf())
  }

  @Test
  fun testReplacedExistingWithVariablesCantReplaceSrc() {
    doTestExisting(arrayOf("/variableidea/test.jar!/"), arrayOf("/srcvariableidea1/test.jar!/"), ArrayUtilRt.EMPTY_STRING_ARRAY)
  }

  @Test
  fun testReplacedExistingWithMultipleJavadocs() {
    doTestExisting(arrayOf("/variableidea/test.jar!/"), arrayOf(), arrayOf("/srcvariableidea1/test.jar!/", "/srcvariableidea11/test.jar!/"))
  }

  @Test
  fun testLibAddLibSource() {
    doTestExisting(arrayOf("/jars/test.jar!/"), arrayOf("/jars/test.jar!/", "/jars/test-2.jar!/"), arrayOf())
  }

  @Test
  fun testLibAddVarSource() {
    doTestExisting(arrayOf("/jars/test.jar!/"), arrayOf("/jars/test.jar!/", "/srcvariableidea/test.jar!/"), arrayOf())
  }

  @Test
  fun testLibReplaceVarSource() {
    doTestExisting(arrayOf("/jars/test.jar!/"), arrayOf("/srcvariableidea/test.jar!/"), arrayOf())
  }

  @Test
  fun testLibvarAddLibSource() {
    doTestExisting(arrayOf("/variableidea/test.jar!/"), arrayOf("/jars/test.jar!/"), arrayOf())
  }

  @Test
  fun testLibvarAddVarSource() {
    doTestExisting(arrayOf("/variableidea/test.jar!/"), arrayOf("/jars/test.jar!/", "/srcvariableidea/test.jar!/"), arrayOf())
  }

  @Test
  fun testLibvarReplaceLibSource() {
    doTestExisting(arrayOf("/variableidea/test.jar!/"), arrayOf("/jars/test.jar!/"), arrayOf())
  }

  @Test
  fun testLibvarReplaceVarSource() {
    doTestExisting(arrayOf("/variableidea/test.jar!/"), arrayOf("/srcvariableidea/test.jar!/"), arrayOf())
  }

  @Test
  fun testVarAddLibSource() {
    doTestExisting(arrayOf("/variableidea/test.jar!/"), arrayOf("/jars/test.jar!/"), arrayOf())
  }

  @Test
  fun testVarAddJavadoc() {
    doTestExisting(arrayOf("/variableidea/test.jar!/"), arrayOf("/variableidea/test.jar!/"), arrayOf("/jars/test.jar!/"))
  }

  @Test
  fun testVarAddVarSource() {
    doTestExisting(arrayOf("/variableidea/test.jar!/"), arrayOf("/variableidea/test.jar!/", "/srcvariableidea/test.jar!/"), arrayOf())
  }

  @Test
  fun testVarReplaceLibSource() {
    doTestExisting(arrayOf("/variableidea/test.jar!/"), arrayOf("/jars/test.jar!/"), arrayOf())
  }

  @Test
  fun testVarReplaceVarSource() {
    doTestExisting(arrayOf("/variableidea/test.jar!/"), arrayOf("/srcvariableidea/test.jar!/"), arrayOf())
  }

  private fun doTestCreate(classRoots: Array<String>, sourceRoots: Array<String>) {
    val testRoot = eclipseTestDataRoot / "modification" / testName.methodName.removePrefix("test").decapitalize()
    val commonRoot = eclipseTestDataRoot / "common" / "testModuleWithClasspathStorage"
    fun addLibrary(project: Project) {
      val module = ModuleManager.getInstance(project).modules.single()
      PsiTestUtil.addLibrary(module, "created", ModuleRootManager.getInstance(module).contentRoots[0].parent.path, classRoots,
                             sourceRoots)
    }
    fun copyClasspathFile(dir: Path) {
      (dir / "expected" / ".classpath").copy(dir / "test" / ".classpath")
    }
    loadEditSaveAndCheck(listOf(commonRoot, testRoot), tempDirectory, true, listOf("test" to "test/test"), ::addLibrary,
                         ::copyClasspathFile)
  }


  private fun doTestExisting(classRoots: Array<String>, sourceRoots: Array<String>, javadocs: Array<String>) {
    val testRoot = eclipseTestDataRoot / "modification" / testName.methodName.removePrefix("test").decapitalize()
    val commonRoot = eclipseTestDataRoot / "common" / "testModuleWithClasspathStorage"
    fun addLibrary(project: Project) {
      ApplicationManager.getApplication().runWriteAction {
        val model = ModuleRootManager.getInstance(ModuleManager.getInstance(project).modules.single()).modifiableModel
        val parentUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, model.contentRoots[0].parent.path)
        val library = model.moduleLibraryTable.getLibraryByName("test.jar")!!
        val libModifiableModel = library.modifiableModel
        val oldClsRoots = libModifiableModel.getUrls(OrderRootType.CLASSES)
        for (oldClsRoot in oldClsRoots) {
          libModifiableModel.removeRoot(oldClsRoot, OrderRootType.CLASSES)
        }
        val oldSrcRoots = libModifiableModel.getUrls(OrderRootType.SOURCES)
        for (oldSrcRoot in oldSrcRoots) {
          libModifiableModel.removeRoot(oldSrcRoot, OrderRootType.SOURCES)
        }
        val oldJdcRoots = libModifiableModel.getUrls(JavadocOrderRootType.getInstance())
        for (oldJavadocRoot in oldJdcRoots) {
          libModifiableModel.removeRoot(oldJavadocRoot, JavadocOrderRootType.getInstance())
        }
        for (classRoot in classRoots) {
          libModifiableModel.addRoot(parentUrl + classRoot, OrderRootType.CLASSES)
        }
        for (sourceRoot in sourceRoots) {
          libModifiableModel.addRoot(parentUrl + sourceRoot, OrderRootType.SOURCES)
        }
        for (javadocRoot in javadocs) {
          libModifiableModel.addRoot(parentUrl + javadocRoot, JavadocOrderRootType.getInstance())
        }
        libModifiableModel.commit()
        model.commit()
      }
    }

    fun copyClasspathAndEmlFiles(dir: Path) {
      (dir / "expected" / ".classpath").copy(dir / "test" / ".classpath")
      (testRoot / "expected" / "ws-internals.eml").copy(dir / "test" / "ws-internals.eml")
    }
    loadEditSaveAndCheck(listOf(commonRoot, testRoot), tempDirectory, true, listOf("test" to "test/ws-internals"),
                         ::addLibrary, ::copyClasspathAndEmlFiles, listOf(".classpath", ".eml"))
  }

  companion object {
    @JvmField
    @RegisterExtension
    val appRule = ApplicationExtension()
  }
}