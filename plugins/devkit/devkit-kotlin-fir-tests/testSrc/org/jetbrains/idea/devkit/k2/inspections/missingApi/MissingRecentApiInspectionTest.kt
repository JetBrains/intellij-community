// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.missingApi

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.PathUtil
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.inspections.missingApi.MissingRecentApiInspection
import org.jetbrains.idea.devkit.inspections.missingApi.MissingRecentApiUsageProcessor
import org.jetbrains.idea.devkit.inspections.pluginModuleFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Base class for tests of [MissingRecentApiInspection] on Java and Kotlin sources.
 */
@TestDataPath("\$CONTENT_ROOT/testData/inspections/missingApi")
@TestApplication
class MissingRecentApiInspectionTest {
  companion object {
    private val BASE_PATH = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/missingApi"

    // The test data path of the fixture, as LightJavaCodeInsightFixtureTestCase.getTestDataPath computes it.
    // The descriptor reads it before the first test.
    private val testDataPath = PlatformTestUtil.getCommunityPath().replace('\\', '/').let { community ->
      if (Path(community + BASE_PATH).exists()) community + BASE_PATH else "$community/../$BASE_PATH"
    }

    private val projectDescriptor = object : LightJavaCodeInsightFixtureTestCase.ProjectDescriptor(LanguageLevel.HIGHEST) {
      override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        super.configureModule(module, model, contentEntry)
        addProjectLibrary(model, "annotations") {
          PsiTestUtil.addProjectLibrary(model, "annotations", listOf(PathUtil.getJarPathForClass(ApiStatus.OverrideOnly::class.java)))
        }
        addProjectLibrary(model, "library") {
          PsiTestUtil.newLibrary("library")
            .classesRoot(testDataPath)
            .externalAnnotationsRoot("$testDataPath/since-2.0")
            .addTo(model)
        }
      }

      // The fixture creates a new module after a test that changed the module. The project libraries of the class stay.
      private fun addProjectLibrary(model: ModifiableRootModel, name: String, create: () -> Unit) {
        val library = LibraryTablesRegistrar.getInstance().getLibraryTable(model.project).getLibraryByName(name)
        if (library != null) model.addLibraryEntry(library) else create()
      }

      override fun getModuleTypeId() = PluginModuleType.ID
    }

    private val projectFixture = lightJavaProjectFixture(projectDescriptor)
  }

  private val pluginModule by pluginModuleFixture(projectFixture, BASE_PATH)
  private val myFixture get() = pluginModule.fixture

  private var inspection = MissingRecentApiInspection()

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
    }
    configureInspection()
    readAction {
      assertAnnotationsFoundForClass("library.RecentClass")
      assertAnnotationsFoundForClass("library.RecentKotlinClass")
    }
    pluginModule.setPluginXml("plugin/plugin.xml")
  }

  private fun configureInspection() {
    myFixture.enableInspections(inspection)
  }

  @AfterEach
  fun tearDown() {
    myFixture.disableInspections(inspection)
  }

  private fun assertAnnotationsFoundForClass(className: String) {
    val psiClass = myFixture.findClass(className)
    val annotations = AnnotationUtil.findAllAnnotations(psiClass, listOf(MissingRecentApiUsageProcessor.AVAILABLE_SINCE_ANNOTATION), false)
    assertThat(annotations).isNotEmpty()
  }

  @Test
  fun `test highlighting of missing API usages in Java file`() {
    myFixture.testHighlighting("plugin/missingApiUsages.java")
  }

  @Test
  fun `test highlighting of missing API usages in Kotlin file`() {
    // otherwise will occur assertion "Access to tree elements not allowed" (probably a bug in Kotlin plugin: KT-34560)
    (myFixture as CodeInsightTestFixtureImpl).setVirtualFileFilter(VirtualFileFilter.NONE)
    myFixture.testHighlighting("plugin/missingApiUsages.kt")
  }

}