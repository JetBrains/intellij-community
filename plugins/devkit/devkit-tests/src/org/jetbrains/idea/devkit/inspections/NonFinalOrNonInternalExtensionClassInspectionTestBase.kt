// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.util.PathUtil
import org.jetbrains.idea.devkit.inspections.quickfix.DevKitInspectionFixTestBase

abstract class NonFinalOrNonInternalExtensionClassInspectionTestBase : DevKitInspectionFixTestBase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(NonFinalOrNonInternalExtensionClassInspection())
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    super.tuneFixture(moduleBuilder)
    moduleBuilder.addLibrary("analysis-api", PathUtil.getJarPathForClass(LocalInspectionEP::class.java))
  }
}