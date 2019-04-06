// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections.missingApi

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.roots.JavaModuleExternalPaths
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.PluginModuleTestCase
import org.jetbrains.idea.devkit.inspections.missingApi.MissingRecentApiInspection
import org.jetbrains.idea.devkit.inspections.missingApi.MissingRecentApiVisitor
import org.jetbrains.idea.devkit.kotlin.inspections.missingApi.project.PluginProjectWithIdeaJdkDescriptor
import org.jetbrains.idea.devkit.kotlin.inspections.missingApi.project.PluginProjectWithIdeaLibraryDescriptor

/**
 * Base class for tests of [MissingRecentApiInspection] on Java and Kotlin sources.
 */
@TestDataPath("\$CONTENT_ROOT/testData/inspections/missingApi")
abstract class MissingRecentApiInspectionTestBase : PluginModuleTestCase() {

  private var inspection = MissingRecentApiInspection()

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
      //Dispose IDEA module or JDK and all attached roots.
      PluginProjectWithIdeaLibraryDescriptor.disposeIdeaLibrary(project)
      PluginProjectWithIdeaJdkDescriptor.disposeIdeaJdk()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  final override fun getTestDataPath() = DevkitJavaTestsUtil.TESTDATA_ABSOLUTE_PATH + "inspections/missingApi"

  /**
   * "Library" classes are put to the same test source root as "client" one,
   * though they represent classes of IDEA and should be put to a separate "library" or JDK.
   *
   * For this test it doesn't matter, since we attach annotations directly.
   */
  private fun configureLibraryFiles() {
    myFixture.configureByFiles(
      "library/RecentClass.java",
      "library/RecentAnnotation.java",
      "library/OldClass.java",
      "library/OldClassWithDefaultConstructor.java",
      "library/OldAnnotation.java",

      "library/RecentKotlinClass.kt",
      "library/RecentKotlinUtils.kt",
      "library/RecentKotlinAnnotation.kt",

      "library/OldKotlinClass.kt",
      "library/OldKotlinAnnotation.kt",
      "library/OldKotlinClassWithDefaultConstructor.kt"
    )
  }


  private fun configureSinceAnnotations() {
    val annotationsRoot = myFixture.copyDirectoryToProject("since-2.0", "extAnnotations").url
    ModuleRootModificationUtil.updateModel(myModule) { model ->
      model
        .getModuleExtension(JavaModuleExternalPaths::class.java)
        .setExternalAnnotationUrls(arrayOf(annotationsRoot))
    }

    assertAnnotationsFoundForClass("library.RecentClass")
    assertAnnotationsFoundForClass("library.RecentKotlinClass")
  }

  private fun assertAnnotationsFoundForClass(className: String) {
    val psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project))!!
    val annotations = AnnotationUtil.findAllAnnotations(psiClass, listOf(MissingRecentApiVisitor.AVAILABLE_SINCE_ANNOTATION), false)
    assertNotEmpty(annotations)
  }

  fun `test highlighting of missing API usages in Java file`() {
    myFixture.configureByFiles("plugin/missingApiUsages.java")
    myFixture.checkHighlighting()
  }

  fun `test highlighting of missing API usages in Kotlin file`() {
    myFixture.configureByFiles("plugin/missingApiUsages.kt")
    myFixture.checkHighlighting()
  }

}

/**
 * Implementation of [MissingRecentApiInspectionTestBase] for sources with configured IDEA library.
 */
class MissingRecentApiWithIdeaLibraryInspectionTest : MissingRecentApiInspectionTestBase() {
  override fun getProjectDescriptor() = PluginProjectWithIdeaLibraryDescriptor()
}

/**
 * Implementation of [MissingRecentApiInspectionTestBase] for sources with configured IDEA JDK.
 */
class MissingRecentApiWithIdeaJdkInspectionTest : MissingRecentApiInspectionTestBase() {
  override fun getProjectDescriptor() = PluginProjectWithIdeaJdkDescriptor()
}