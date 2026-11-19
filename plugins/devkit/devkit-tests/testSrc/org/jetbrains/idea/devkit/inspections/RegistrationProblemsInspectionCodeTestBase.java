// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

/**
 * @deprecated Use {@link RegistrationProblemsInspectionCodeFixtureKt#registrationProblemsInspectionCodeFixture} in a JUnit 5 test.
 */
@Deprecated
public abstract class RegistrationProblemsInspectionCodeTestBase extends PluginModuleTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new RegistrationProblemsInspection());
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction {}");
    myFixture.addClass("package com.intellij.openapi.components; public interface BaseComponent {}");
  }
}
