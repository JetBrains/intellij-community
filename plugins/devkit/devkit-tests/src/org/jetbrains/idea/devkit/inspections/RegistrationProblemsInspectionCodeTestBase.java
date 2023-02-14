// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

public abstract class RegistrationProblemsInspectionCodeTestBase extends PluginModuleTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new RegistrationProblemsInspection());
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction {}");
    myFixture.addClass("package com.intellij.openapi.components; public interface BaseComponent {}");
  }
}
