// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public abstract class PluginXmlDomInspectionTestBase extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction {}");
    myFixture.addClass("package com.intellij.openapi.components; public interface ApplicationComponent {}");
    myFixture.enableInspections(new PluginXmlDomInspection());
  }
}
