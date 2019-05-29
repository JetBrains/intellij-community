// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections.missingApi

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.JavaModuleExternalPaths
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.inspections.PluginModuleTestCase
import org.jetbrains.idea.devkit.inspections.missingApi.MissingRecentApiInspection
import org.jetbrains.idea.devkit.inspections.missingApi.MissingRecentApiUsageProcessor
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.module.PluginModuleType

/**
 * Base class for tests of [MissingRecentApiInspection] on Java and Kotlin sources.
 */
@TestDataPath("\$CONTENT_ROOT/testData/inspections/missingApi")
class MissingRecentApiInspectionTest : PluginModuleTestCase() {

  private val projectDescriptor = object : LightCodeInsightFixtureTestCase.ProjectDescriptor(LanguageLevel.HIGHEST) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      PsiTestUtil.addProjectLibrary(model, "annotations", listOf(PathUtil.getJarPathForClass(ApiStatus.OverrideOnly::class.java)))
      PsiTestUtil.addProjectLibrary(model, "library", listOf(testDataPath))
      PsiTestUtil.addProjectLibrary(model, "kotlin-stdlib", listOf(PathUtil.getJarPathForClass(Function::class.java)))
    }

    override fun getModuleType() = PluginModuleType.getInstance()
  }

  private var inspection = MissingRecentApiInspection()

  override fun getProjectDescriptor() = projectDescriptor

  override fun setUp() {
    super.setUp()
    configureInspection()
    configureLibraryFiles()
    configureSinceAnnotations()
    setPluginXml("plugin/plugin.xml")
  }

  private fun configureInspection() {
    myFixture.enableInspections(inspection)
  }

  override fun tearDown() {
    try {
      myFixture.disableInspections(inspection)
    }
    finally {
      super.tearDown()
    }
  }

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/missingApi"

  /**
   * "Library" classes are put to the same test source root as "client" one,
   * though they represent classes of IDEA and should be put to a separate "library" or JDK.
   *
   * For this test it doesn't matter, since we attach annotations directly.
   */
  private fun configureLibraryFiles() {
    myFixture.configureByFiles(
      "library/RecentClass.java",
      "library/RecentInterface.java",
      "library/RecentSamInterface.java",
      "library/RecentAnnotation.java",
      "library/OldClass.java",
      "library/OldClassWithDefaultConstructor.java",
      "library/OldAnnotation.java",

      "library/RecentKotlinClass.kt",
      "library/RecentKotlinInterface.kt",
      "library/RecentKotlinUtils.kt",
      "library/RecentKotlinAnnotation.kt",

      "library/OldKotlinClass.kt",
      "library/OldKotlinAnnotation.kt",
      "library/OldKotlinClassWithDefaultConstructor.kt"
    )
  }


  private fun configureSinceAnnotations() {
    val annotationsRoot = myFixture.copyDirectoryToProject("since-2.0", "extAnnotations").url
    ModuleRootModificationUtil.updateModel(module) { model ->
      model
        .getModuleExtension(JavaModuleExternalPaths::class.java)
        .setExternalAnnotationUrls(arrayOf(annotationsRoot))
    }

    assertAnnotationsFoundForClass("library.RecentClass")
    assertAnnotationsFoundForClass("library.RecentKotlinClass")
  }

  private fun assertAnnotationsFoundForClass(className: String) {
    val psiClass = myFixture.findClass(className)
    val annotations = AnnotationUtil.findAllAnnotations(psiClass, listOf(MissingRecentApiUsageProcessor.AVAILABLE_SINCE_ANNOTATION), false)
    assertNotEmpty(annotations)
  }

  fun `test highlighting of missing API usages in Java file`() {
    myFixture.testHighlighting("plugin/missingApiUsages.java")
  }

  fun `test highlighting of missing API usages in Kotlin file`() {
    myFixture.testHighlighting("plugin/missingApiUsages.kt")
  }

}