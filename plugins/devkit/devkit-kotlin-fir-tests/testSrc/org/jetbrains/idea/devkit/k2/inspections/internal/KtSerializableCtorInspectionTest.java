// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.internal.SerializableCtorInspectionTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider;import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProviderKt;

@TestDataPath("$CONTENT_ROOT/testData/inspections/serializableCtor")
public class KtSerializableCtorInspectionTest extends SerializableCtorInspectionTestBase implements ExpectedPluginModeProvider {

  @Override
  public KotlinPluginMode getPluginMode() {
    return KotlinPluginMode.K2;
  }

  @Override
  protected void setUp() throws Exception {
    ExpectedPluginModeProviderKt.setUpWithKotlinPlugin(this, getTestRootDisposable(), () -> super.setUp());
  }

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/serializableCtor";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "kt";
  }

  public void testCorrectAnnotatedConstructor() {
    doTest();
  }

  public void testSerializableClassButPropertyMappingAnnotationNotAvailable() {
    doTest(true);
  }

  public void testNotSerializableClass() {
    doTest();
  }

  public void testSerializableClassButDoesNotContainSerialVersionUidField() {
    doTest();
  }

  public void testClassContainingSerialVersionUidFieldButIsNotSerializable() {
    doTest();
  }

  public void testNotAnnotatedConstructor() {
    doTest();
  }

  public void testNotAnnotatedMultipleConstructors() {
    doTest();
  }

  public void testNotAnnotatedAndAnnotatedConstructorsInSingleClass() {
    doTest();
  }
}
