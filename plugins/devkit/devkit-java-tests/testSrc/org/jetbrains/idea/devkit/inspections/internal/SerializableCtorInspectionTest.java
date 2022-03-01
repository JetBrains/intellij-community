package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

public class SerializableCtorInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/serializableCtor";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SerializableCtorInspection());
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

  private void addPropertyMappingClass() {
    myFixture.addClass("package com.intellij.serialization;\n" +
                       "public @interface PropertyMapping { String[] value(); }");
  }

  private void doTest() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }
}
