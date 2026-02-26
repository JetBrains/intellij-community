// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.execution.junit.codeInspection.JUnit5ConverterInspection
import com.intellij.junit.testFramework.MavenTestLib.JUNIT4
import com.intellij.junit.testFramework.MavenTestLib.JUNIT5
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.pom.java.LanguageLevel.Companion.HIGHEST

private val descriptor = JUnitProjectDescriptor(HIGHEST, JUNIT4, JUNIT5)

abstract class JUnit5ConverterInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: JUnit5ConverterInspection = JUnit5ConverterInspection()
  override fun getProjectDescriptor(): JUnitProjectDescriptor = descriptor
}