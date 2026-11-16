// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.kotlin.inspections.InstanceIElementTypeFieldInspection

/**
 * Creates a [LightDevKitInspectionFixture] of the [InstanceIElementTypeFieldInspection] tests for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. The fixture enables the inspection.
 * The project has the stubs of `IElementType`, `MyTokenType` and `Language`.
 */
fun instanceIElementTypeFieldFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
): TestFixture<LightDevKitInspectionFixture> = testFixture("instanceIElementTypeFieldFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, DevkitKtTestsUtil.TESTDATA_PATH + "inspections/instanceIElementTypeField").init()
  val fixture = devKit.fixture
  // Add mock IElementType class
  fixture.addClass("""
    package com.intellij.psi.tree;
    public class IElementType {
      public IElementType(String debugName) {}
      public IElementType(String debugName, Object language) {}
    }
  """.trimIndent())
  // Add IElementType subtype for testing
  fixture.addClass("""
    package com.intellij.psi.tree;
    public class MyTokenType extends IElementType {
      public MyTokenType(String debugName) { super(debugName); }
    }
  """.trimIndent())
  // Add Language class for testing
  fixture.addClass("""
    package com.intellij.lang;
    public abstract class Language {
      public static final Language ANY = new Language() {};
      protected Language() {}
    }
  """.trimIndent())
  fixture.enableInspections(InstanceIElementTypeFieldInspection())
  initialized(devKit) {}
}