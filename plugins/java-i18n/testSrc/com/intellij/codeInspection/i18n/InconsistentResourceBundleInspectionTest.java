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
package com.intellij.codeInspection.i18n;

import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.i18n.inconsistentResourceBundle.*;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * @author Dmitry Batkovich
 */
@SuppressWarnings("unchecked")
public class InconsistentResourceBundleInspectionTest extends JavaCodeInsightFixtureTestCase {
  private final InconsistentResourceBundleInspection myInspection = new InconsistentResourceBundleInspection();

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("java-i18n") + "/testData/inspections/inconsistentResourceBundle";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myInspection.disableAllProviders();
  }

  @Override
  protected void tearDown() throws Exception {
    myInspection.clearSettings();
    super.tearDown();
  }

  public void testInconsistentPlaceholders() {
    myInspection.enableProviders(PropertiesPlaceholdersInspectionProvider.class);
    doTest();
  }

  public void testInconsistentValuesEnds() {
    myInspection.enableProviders(InconsistentPropertiesEndsInspectionProvider.class);
    doTest();
  }

  public void testSimple() {
    myInspection.enableProviders(PropertiesKeysConsistencyInspectionProvider.class,
                                 MissingTranslationsInspectionProvider.class,
                                 DuplicatedPropertiesInspectionProvider.class);
    doTest();
  }

  private void doTest() {
    myFixture.testInspection(getTestName(false), new GlobalInspectionToolWrapper(myInspection));
  }
}
