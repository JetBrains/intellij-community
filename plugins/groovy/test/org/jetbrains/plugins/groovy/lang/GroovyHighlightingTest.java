/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyUncheckedAssignmentOfMemberOfRawTypeInspection;
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialConditionalInspection;
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialIfInspection;
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection;
import org.jetbrains.plugins.groovy.codeInspection.unassignedVariable.UnassignedVariableAccessInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUntypedAccessInspection;
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection;

import java.io.IOException;

/**
 * @author peter
 */
public class GroovyHighlightingTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return "/svnPlugins/groovy/testdata/highlighting/";
  }

  public void testDuplicateClosurePrivateVariable() throws Throwable {
    doTest();
  }

  public void testClosureRedefiningVariable() throws Throwable {
    doTest();
  }

  private void doTest(LocalInspectionTool... tools) throws Exception {
    myFixture.enableInspections(tools);
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

  public void testDontSimplifyString() throws Throwable { doTest(new GroovyTrivialIfInspection(), new GroovyTrivialConditionalInspection()); }

  public void testRawMethodAccess() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawFieldAccess() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawArrayStyleAccess() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawArrayStyleAccessToMap() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawArrayStyleAccessToList() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testIncompatibleTypesAssignments() throws Throwable { doTest(new GroovyAssignabilityCheckInspection()); }

  public void testAnonymousClassConstructor() throws Throwable {doTest();}
  public void testAnonymousClassAbstractMethod() throws Throwable {doTest();}
  public void testAnonymousClassStaticMethod() throws Throwable {doTest();}
  public void testAnonymousClassShoudImplementMethods() throws Throwable {doTest();}

  
  public void testDefaultMapConstructorNamedArgs() throws Throwable {doTest();}
  public void testDefaultMapConstructorNamedArgsError() throws Throwable {doTest();}
  public void testDefaultMapConstructorWhenDefConstructorExists() throws Throwable {doTest();}

  public void testUnresolvedLhsAssignment() throws Throwable { doTest(new GroovyUnresolvedAccessInspection()); }

  public void testMissingReturnWithLastLoop() throws Throwable { doTest(new MissingReturnInspection()); }
  public void testMissingReturnWithUnknownCall() throws Throwable { doTest(new MissingReturnInspection()); }
  public void testMissingReturnWithIf() throws Throwable { doTest(new MissingReturnInspection()); }
  public void testMissingReturnWithAssertion() throws Throwable { doTest(new MissingReturnInspection()); }
  public void testMissingReturnThrowException() throws Throwable { doTest(new MissingReturnInspection()); }
  public void testMissingReturnTryCatch() throws Throwable { doTest(new MissingReturnInspection()); }
  public void testMissingReturnLastNull() throws Throwable { doTest(new MissingReturnInspection()); }

  public void testUnresolvedMethodCallWithTwoDeclarations() throws Throwable{
    doTest();
  }
  
  public void testUnresolvedAccess() throws Exception { doTest(new GroovyUnresolvedAccessInspection()); }
  public void testUntypedAccess() throws Exception { doTest(new GroovyUntypedAccessInspection()); }
  
  public void testUnassigned1() throws Exception { doTest(new UnassignedVariableAccessInspection()); }
  public void testUnassigned2() throws Exception { doTest(new UnassignedVariableAccessInspection()); }
  public void testUnassigned3() throws Exception { doTest(new UnassignedVariableAccessInspection()); }
  public void testUnassignedTryFinally() throws Exception { doTest(new UnassignedVariableAccessInspection()); }
  
  public void testUnusedVariable() throws Exception { doTest(new UnusedDefInspection()); }
  public void testDefinitionUsedInClosure() throws Exception { doTest(new UnusedDefInspection()); }
  public void testDefinitionUsedInClosure2() throws Exception { doTest(new UnusedDefInspection()); }
  public void testDuplicateInnerClass() throws Throwable{doTest();}

  public void testThisInStaticContext() throws Throwable {doTest();}
  public void testSuperWithNotEnclosingClass() throws Throwable {doTest();}
  public void testThisWithWrongQualifier() throws Throwable {doTest();}

  public void testModifiersInPackageAndImportStatements() throws Throwable {
    myFixture.copyFileToProject(getTestName(false) + ".groovy", "x/"+getTestName(false)+".groovy");
    myFixture.testHighlighting(true, false, false, "x/"+getTestName(false)+".groovy");
  }
}