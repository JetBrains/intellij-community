// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.execution.junit.codeInspection.JUnit5AssertionsConverterInspection
import com.intellij.junit.testFramework.JUnitLibrary.*
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.pom.java.LanguageLevel

abstract class JUnit5AssertionsConverterInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: JUnit5AssertionsConverterInspection = JUnit5AssertionsConverterInspection()
  override fun getProjectDescriptor(): JUnitProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST, JUNIT4, JUNIT5, HAMCREST)
}