// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.missingApi.project.PluginProjectWithIdeaJdkDescriptor
import org.jetbrains.idea.devkit.inspections.missingApi.project.PluginProjectWithIdeaLibraryDescriptor

@TestDataPath("\$CONTENT_ROOT/testData/inspections/missingApi")
abstract class JavaMissingRecentApiInspectionTestBase : MissingRecentApiInspectionTestBase() {

  /**
   * Plugin is compatible with [1.0; 999.0].
   * Some APIs were introduced in 2.0.
   * => There may be problems with [1.0; 2.0)
   */
  fun `test highlighting of missing API usages in Java file`() {
    myFixture.configureByFiles("plugin/missingApiUsages.java")
    myFixture.checkHighlighting()
  }
}

/**
 * Implementation of [MissingRecentApiInspectionTestBase] for Java sources with configured IDEA library.
 */
class JavaIdeaLibraryMissingRecentApiInspectionTest : JavaMissingRecentApiInspectionTestBase() {
  override fun getProjectDescriptor() = PluginProjectWithIdeaLibraryDescriptor()
}

/**
 * Implementation of [MissingRecentApiInspectionTestBase] for Java sources with configured IDEA JDK.
 */
class JavaIdeaJdkMissingRecentApiInspectionTest : JavaMissingRecentApiInspectionTestBase() {
  override fun getProjectDescriptor() = PluginProjectWithIdeaJdkDescriptor()
}