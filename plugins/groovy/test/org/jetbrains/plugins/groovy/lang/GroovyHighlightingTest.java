/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang;

import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialConditionalInspection;
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialIfInspection;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyUncheckedAssignmentOfMemberOfRawTypeInspection;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;

import java.io.IOException;

/**
 * @author peter
 */
public class GroovyHighlightingTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return "/svnPlugins/groovy/testdata/highlighting/";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
  }

  public void testDuplicateClosurePrivateVariable() throws Throwable {
    doTest();
  }

  public void testClosureRedefiningVariable() throws Throwable {
    doTest();
  }

  private void doTest() throws Throwable {
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".groovy");
  }

  public void testCircularInheritance() throws Throwable {
    doTest();
  }

  public void testEmptyTupleType() throws Throwable {
    doTest();
  }

  public void testMapDeclaration() throws Throwable {
    doTest();
  }

  public void testShouldntImplementGroovyObjectMethods() throws Throwable {
    addGroovyObject();
    myFixture.addFileToProject("Foo.groovy", "class Foo {}");
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java");
  }

  public void testJavaClassImplementingGroovyInterface() throws Throwable {
    addGroovyObject();
    myFixture.addFileToProject("Foo.groovy", "interface Foo {}");
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java");
  }

  private void addGroovyObject() throws IOException {
    myFixture.addClass("package groovy.lang;" +
                       "public interface GroovyObject  {\n" +
                       "    java.lang.Object invokeMethod(java.lang.String s, java.lang.Object o);\n" +
                       "    java.lang.Object getProperty(java.lang.String s);\n" +
                       "    void setProperty(java.lang.String s, java.lang.Object o);\n" +
                       "    groovy.lang.MetaClass getMetaClass();\n" +
                       "    void setMetaClass(groovy.lang.MetaClass metaClass);\n" +
                       "}");
  }

  public void testDuplicateFields() throws Throwable {
    doTest();
  }

  public void testNoDuplicationThroughClosureBorder() throws Throwable {
    myFixture.addClass("package groovy.lang; public interface Closure {}");
    doTest();
  }

  public void testRecursiveMethodTypeInference() throws Throwable {
    doTest();
  }

  public void testDontSimplifyString() throws Throwable {
    myFixture.enableInspections(new GroovyTrivialIfInspection(), new GroovyTrivialConditionalInspection());
    doTest();
  }

  public void testRawMethodAccess() throws Throwable {
    myFixture.enableInspections(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection());
    doTest();
  }

  public void testRawFieldAccess() throws Throwable {
    myFixture.enableInspections(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection());
    doTest();
  }

  public void testRawArrayStyleAccess() throws Throwable {
    myFixture.enableInspections(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection());
    doTest();
  }

  public void testRawArrayStyleAccessToMap() throws Throwable {
    myFixture.enableInspections(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection());
    doTest();
  }

  public void testRawArrayStyleAccessToList() throws Throwable {
    myFixture.enableInspections(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection());
    doTest();
  }

  public void testIncompatibleTypesAssignments() throws Throwable {
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection());
    doTest();
  }
}