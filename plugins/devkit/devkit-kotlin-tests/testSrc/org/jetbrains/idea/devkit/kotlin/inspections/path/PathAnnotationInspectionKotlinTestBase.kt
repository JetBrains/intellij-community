// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.path

import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

abstract class PathAnnotationInspectionKotlinTestBase : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "kt"

  override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

  override fun setUp() {
    super.setUp()
    ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }
}