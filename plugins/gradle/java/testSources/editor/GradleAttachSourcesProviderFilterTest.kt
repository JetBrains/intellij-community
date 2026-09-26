// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.editor

import com.intellij.codeInsight.daemon.impl.AttachSourcesNotificationProvider
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.ui.EditorNotificationPanel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.junit.Test
import org.mockito.Mockito
import java.awt.Container
import java.util.function.Consumer
import kotlin.io.path.deleteIfExists

class GradleAttachSourcesProviderFilterTest : GradleImportingTestCase() {

  private companion object {
    private const val DEPENDENCY = "junit:junit:4.12"
    private const val DEPENDENCY_NAME = "Gradle: junit:junit:4.12"
    private const val DEPENDENCY_JAR = "junit-4.12.jar"
    private const val DEPENDENCY_SOURCES_JAR = "junit-4.12-sources.jar"
    private const val CLASS_FROM_DEPENDENCY = "junit.framework.Test"
    private const val DEPENDENCY_SOURCES_JAR_CACHE_PATH = "caches/modules-2/files-2.1/junit/junit/4.12/" +
                                                          "a6c32b40bf3d76eca54e3c601e5d1470c86fcdfa/$DEPENDENCY_SOURCES_JAR"
  }

  override fun setUp() {
    super.setUp()
    removeCachedLibrary()
  }

  @Test
  fun `test only one default attach sources button is shown for a gradle library`(): Unit = runBlocking {
    importProject {
      withJavaPlugin()
      withIdeaPlugin()
      withMavenCentral()
      addTestImplementationDependency(DEPENDENCY)
      addPrefix("idea.module.downloadSources = false")
    }

    assertModules("project", "project.main", "project.test")

    assertLibraryOrderEntry("project.test", DEPENDENCY_NAME) {
      assertProductionClasses(DEPENDENCY_JAR)
      assertNoSources()
    }

    IndexingTestUtil.waitUntilIndexesAreReady(myProject)

    val provider = AttachSourcesNotificationProvider()
    runReadAction {
      val psiFile = JavaPsiFacade.getInstance(myProject)
        .findClass(CLASS_FROM_DEPENDENCY, GlobalSearchScope.allScope(myProject))!!
        .containingFile
        .virtualFile
      val component = provider.collectNotificationData(myProject, psiFile)!!.apply(Mockito.mock()) as EditorNotificationPanel
      assertEquals("Unexpected notification panel layout. Please fix the test accordingly to the expected layout", 2, component.components.size)
      val actionPanel = component.components[1] as Container
      assertEquals("Only one action should be available", 1, actionPanel.components.size)
    }
  }

  private fun assertLibraryOrderEntry(moduleName: String, dependencyName: String, fn: LibraryOrderEntry.() -> Unit) {
    val libraries = getModuleLibDeps(moduleName, dependencyName)
    assertThat(libraries).hasSize(1)
    val libraryOrderEntry = libraries.first()
    fn(libraryOrderEntry)
  }

  private fun LibraryOrderEntry.assertProductionClasses(jarName: String) {
    assertThat(getRootFiles(OrderRootType.CLASSES))
      .hasSize(1)
      .allSatisfy(Consumer { assertEquals(jarName, it.name) })
  }

  private fun LibraryOrderEntry.assertNoSources() {
    assertThat(getRootFiles(OrderRootType.SOURCES)).hasSize(0)
  }

  private fun removeCachedLibrary(cachePath: String = DEPENDENCY_SOURCES_JAR_CACHE_PATH) = gradleUserHome.resolve(cachePath).run {
    deleteIfExists()
  }
}