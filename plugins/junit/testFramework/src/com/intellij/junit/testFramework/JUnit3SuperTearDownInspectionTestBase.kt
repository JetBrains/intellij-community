// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.execution.junit.codeInspection.JUnit3SuperTearDownInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class JUnit3SuperTearDownInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: JUnit3SuperTearDownInspection = JUnit3SuperTearDownInspection()
  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST, JUnitLibrary.JUNIT3)
}