// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/serializableCtor")
public class SerializableCtorInspectionTest extends SerializableCtorInspectionTestBase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/serializableCtor";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "java";
  }

  public void testCorrectAnnotatedConstructor() {
    doTest(false);
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
