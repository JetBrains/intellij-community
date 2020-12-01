// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.storage.ClassPathStorageUtil
import com.intellij.openapi.roots.impl.storage.ClasspathStorage
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.copy
import org.jetbrains.jps.eclipse.model.JpsEclipseClasspathSerializer
import org.junit.Assume.assumeTrue
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.div

@ExperimentalPathApi
class ChangeStorageTypeTest {
  @JvmField
  @Rule
  val tempDirectory = TempDirectory()

  @JvmField
  @Rule
  val testName = TestName()

  @Test
  fun `switch to default storage`() {
    val commonRoot = eclipseTestDataRoot / "common" / "testModuleWithClasspathStorage"
    val classpathRoot = eclipseTestDataRoot / "storageType" / "eclipse"
    loadEditSaveAndCheck(listOf(commonRoot, classpathRoot), tempDirectory, false, listOf("test" to "test/test"),
                         { switchStorage(it, ClassPathStorageUtil.DEFAULT_STORAGE) },
                         { (eclipseTestDataRoot / "storageType" / "default" / "test.iml").copy(it / "test" / "test.iml")})
  }

  @Test
  fun `switch to classpath storage`() {
    assumeTrue(ProjectModelRule.isWorkspaceModelEnabled)
    val commonRoot = eclipseTestDataRoot / "common" / "testModuleWithClasspathStorage"
    val defaultRoot = eclipseTestDataRoot / "storageType" / "default"
    loadEditSaveAndCheck(listOf(commonRoot, defaultRoot), tempDirectory, false, listOf("test" to "test/test"),
                         { switchStorage(it, JpsEclipseClasspathSerializer.CLASSPATH_STORAGE_ID) },
                         {
                           (eclipseTestDataRoot / "common" / "testModuleWithClasspathStorage" / "test.iml").copy(it / "test" / "test.iml")
                           (eclipseTestDataRoot / "storageType" / "eclipse" / "test" / ".classpath").copy(it / "test" / ".classpath")
                         })
  }

  private fun switchStorage(project: Project, storageId: String) {
    val module = ModuleManager.getInstance(project).modules.single()
    ClasspathStorage.setStorageType(ModuleRootManager.getInstance(module), storageId)
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

}