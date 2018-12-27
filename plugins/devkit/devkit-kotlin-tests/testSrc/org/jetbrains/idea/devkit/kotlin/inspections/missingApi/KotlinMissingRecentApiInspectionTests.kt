// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections.missingApi

import org.jetbrains.idea.devkit.inspections.missingApi.MissingRecentApiInspectionTestBase
import org.jetbrains.idea.devkit.inspections.missingApi.project.PluginProjectWithIdeaJdkDescriptor
import org.jetbrains.idea.devkit.inspections.missingApi.project.PluginProjectWithIdeaLibraryDescriptor

abstract class KotlinMissingRecentApiInspectionTestBase : MissingRecentApiInspectionTestBase() {

  /**
   * Plugin is compatible with [1.0; 999.0].
   * Some APIs were introduced in 2.0.
   * => There may be compatibility problems with [1.0; 2.0)
   */
  fun `test highlighting of missing API usages in Kotlin file`() {
    myFixture.configureByFiles("plugin/missingApiUsages.kt")
    myFixture.checkHighlighting()
  }

}

/**
 * Implementation of [MissingRecentApiInspectionTestBase] for Kotlin sources with configured IDEA library.
 */
class KotlinIdeaLibraryMissingRecentApiInspectionTest : KotlinMissingRecentApiInspectionTestBase() {
  override fun getProjectDescriptor() = PluginProjectWithIdeaLibraryDescriptor()
}

/**
 * Implementation of [MissingRecentApiInspectionTestBase] for Kotlin sources with configured IDEA JDK.
 */
class KotlinIdeaJdkMissingRecentApiInspectionTest : KotlinMissingRecentApiInspectionTestBase() {
  override fun getProjectDescriptor() = PluginProjectWithIdeaJdkDescriptor()
}