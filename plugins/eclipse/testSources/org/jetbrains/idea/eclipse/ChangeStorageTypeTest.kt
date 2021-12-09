// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.storage.ClassPathStorageUtil
import com.intellij.openapi.roots.impl.storage.ClasspathStorage
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.rules.TestNameExtension
import com.intellij.util.io.copy
import org.jetbrains.jps.eclipse.model.JpsEclipseClasspathSerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.div

@ExperimentalPathApi
class ChangeStorageTypeTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @JvmField
  @RegisterExtension
  val testName = TestNameExtension()

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
    @RegisterExtension
    val appRule = ApplicationExtension()
  }

}