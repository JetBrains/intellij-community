// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.execution.junit.codeInspection.HamcrestAssertionsConverterInspection
import com.intellij.junit.testFramework.MavenTestLib.HAMCREST
import com.intellij.junit.testFramework.MavenTestLib.JUNIT4
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.pom.java.LanguageLevel.Companion.HIGHEST
import com.intellij.testFramework.LightProjectDescriptor

private val descriptor = JUnitProjectDescriptor(HIGHEST, JUNIT4, HAMCREST)

abstract class HamcrestAssertionsConverterInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: HamcrestAssertionsConverterInspection = HamcrestAssertionsConverterInspection()
  override fun getProjectDescriptor(): LightProjectDescriptor = descriptor
}