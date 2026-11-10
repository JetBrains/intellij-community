/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.inheritance.ImplicitSubclassInspection;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.inheritance.AbstractClassNeverImplementedInspection;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/implicitUsage")
@TestApplication
public class DevKitImplicitUsageProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/implicitUsage");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();

    myFixture.addClass("package com.intellij.util.xml; public interface DomElement {}");
    myFixture.addClass("package com.intellij.util.xml; public interface DomElementVisitor {}");
    myFixture.addClass("package com.intellij.util.xml; public interface GenericAttributeValue<T> extends DomElement {}");

    myFixture.addClass("package com.intellij.jam; public interface JamElement {}");

    myFixture.addClass("package com.intellij.mcpserver.annotations; public @interface McpTool {}");
    myFixture.addClass("package com.intellij.mcpserver.annotations; public @interface McpDescription { String value(); }");
    myFixture.addClass("package com.intellij.mcpserver; public interface McpToolset {}");
  }

  @Test
  public void testImplicitUsagesDomElement() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    enableImplicitUsageInspections();
    myFixture.testHighlighting("ImplicitUsagesDomElement.java");
  }

  @Test
  public void testImplicitUsagesDomElementVisitor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    enableImplicitUsageInspections();
    myFixture.testHighlighting("ImplicitUsagesDomElementVisitor.java");
  }

  @Test
  public void testImplicitUsagesMcpToolMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    enableImplicitUsageInspections();
    myFixture.testHighlighting("ImplicitUsagesMcpToolMethod.java");
  }

  private void enableImplicitUsageInspections() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new UnusedDeclarationInspectionBase(true));
  }

  @Test
  public void testImplementedAtRuntimeDomElementImpl() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    enableImplementedAtRuntimeInspections();
    myFixture.testHighlighting("ImplementedAtRuntimeDomElementImpl.java");
  }

  @Test
  public void testManualJamElementInstantiation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(ImplicitSubclassInspection.class);
    myFixture.testHighlighting("ManualJamElementInstantiation.java");
  }

  private void enableImplementedAtRuntimeInspections() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new AbstractClassNeverImplementedInspection());
  }
}
