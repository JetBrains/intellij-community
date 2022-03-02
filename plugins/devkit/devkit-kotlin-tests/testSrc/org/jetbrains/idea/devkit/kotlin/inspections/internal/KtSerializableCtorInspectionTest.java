// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.internal.SerializableCtorInspectionTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/serializableCtor")
public class KtSerializableCtorInspectionTest extends SerializableCtorInspectionTestBase {

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/serializableCtor";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "kt";
  }

  public void testCorrectAnnotatedConstructor() {
    addPropertyMappingClass();
    doTest();
  }

  public void testSerializableClassButPropertyMappingAnnotationNotAvailable() {
    // no @PropertyMapping in the project
    doTest();
  }

  public void testNotSerializableClass() {
    addPropertyMappingClass();
    doTest();
  }

  public void testSerializableClassButDoesNotContainSerialVersionUidField() {
    addPropertyMappingClass();
    doTest();
  }

  public void testClassContainingSerialVersionUidFieldButIsNotSerializable() {
    addPropertyMappingClass();
    doTest();
  }

  public void testNotAnnotatedConstructor() {
    addPropertyMappingClass();
    doTest();
  }

  public void testNotAnnotatedMultipleConstructors() {
    addPropertyMappingClass();
    doTest();
  }

  public void testNotAnnotatedAndAnnotatedConstructorsInSingleClass() {
    addPropertyMappingClass();
    doTest();
  }
}
