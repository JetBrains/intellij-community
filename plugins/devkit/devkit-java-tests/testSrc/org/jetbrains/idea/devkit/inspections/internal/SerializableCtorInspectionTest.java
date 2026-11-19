// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.internal.SerializableCtorInspectionFixtureKt.serializableCtorInspectionFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/serializableCtor")
@TestApplication
public class SerializableCtorInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<SerializableCtorInspectionFixture> serializableCtor =
    serializableCtorInspectionFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/serializableCtor");

  @Test
  public void testCorrectAnnotatedConstructor() {
    serializableCtor.get().doTest(false);
  }

  @Test
  public void testSerializableClassButPropertyMappingAnnotationNotAvailable() {
    serializableCtor.get().doTest(true);
  }

  @Test
  public void testNotSerializableClass() {
    serializableCtor.get().doTest();
  }

  @Test
  public void testSerializableClassButDoesNotContainSerialVersionUidField() {
    serializableCtor.get().doTest();
  }

  @Test
  public void testClassContainingSerialVersionUidFieldButIsNotSerializable() {
    serializableCtor.get().doTest();
  }

  @Test
  public void testNotAnnotatedConstructor() {
    serializableCtor.get().doTest();
  }

  @Test
  public void testNotAnnotatedMultipleConstructors() {
    serializableCtor.get().doTest();
  }

  @Test
  public void testNotAnnotatedAndAnnotatedConstructorsInSingleClass() {
    serializableCtor.get().doTest();
  }
}
