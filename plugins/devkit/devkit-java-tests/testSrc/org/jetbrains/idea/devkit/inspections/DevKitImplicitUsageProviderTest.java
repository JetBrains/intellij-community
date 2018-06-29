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
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.siyeh.ig.inheritance.AbstractClassNeverImplementedInspection;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/implicitUsage")
public class DevKitImplicitUsageProviderTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/implicitUsage";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package com.intellij.util.xml; public interface DomElement {}");
    myFixture.addClass("package com.intellij.util.xml; public interface DomElementVisitor {}");
    myFixture.addClass("package com.intellij.util.xml; public interface GenericAttributeValue<T> extends DomElement {}");

    myFixture.addClass("package com.intellij.jam; public interface JamElement {}");
  }

  public void testImplicitUsagesDomElement() {
    enableImplicitUsageInspections();
    myFixture.testHighlighting("ImplicitUsagesDomElement.java");
  }

  public void testImplicitUsagesDomElementVisitor() {
    enableImplicitUsageInspections();
    myFixture.testHighlighting("ImplicitUsagesDomElementVisitor.java");
  }

  private void enableImplicitUsageInspections() {
    myFixture.enableInspections(new UnusedDeclarationInspectionBase(true));
  }


  public void testImplementedAtRuntimeDomElementImpl() {
    enableImplementedAtRuntimeInspections();
    myFixture.testHighlighting("ImplementedAtRuntimeDomElementImpl.java");
  }

  public void testImplementedAtRuntimeJamElementImpl() {
    enableImplementedAtRuntimeInspections();
    myFixture.testHighlighting("ImplementedAtRuntimeJamElementImpl.java");
  }

  private void enableImplementedAtRuntimeInspections() {
    myFixture.enableInspections(new AbstractClassNeverImplementedInspection());
  }
}
