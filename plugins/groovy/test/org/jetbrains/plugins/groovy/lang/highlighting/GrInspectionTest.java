// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyResultOfAssignmentUsedInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyDocCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyInArgumentCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyLabeledStatementInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyRangeTypeCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyResultOfObjectAllocationIgnoredInspection;
import org.jetbrains.plugins.groovy.codeInspection.confusing.ClashingGettersInspection;
import org.jetbrains.plugins.groovy.codeInspection.confusing.DelegatesToInspection;
import org.jetbrains.plugins.groovy.codeInspection.confusing.GrDeprecatedAPIUsageInspection;
import org.jetbrains.plugins.groovy.codeInspection.confusing.GrPackageInspection;
import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyOctalIntegerInspection;
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialConditionalInspection;
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialIfInspection;
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyUnnecessaryContinueInspection;
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyUnnecessaryReturnInspection;
import org.jetbrains.plugins.groovy.codeInspection.declaration.GrMethodMayBeStaticInspection;
import org.jetbrains.plugins.groovy.codeInspection.exception.GroovyEmptyCatchBlockInspection;
import org.jetbrains.plugins.groovy.codeInspection.metrics.GroovyOverlyLongMethodInspection;
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUntypedAccessInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GrHighlightingFixture> highlighting = grHighlightingFixture(projectFixture);

  @Test
  public void testDontSimplifyString() { highlighting.get().doTest(new GroovyTrivialIfInspection(), new GroovyTrivialConditionalInspection()); }

  @Test
  public void testSingleAllocationInClosure() { highlighting.get().doTest(new GroovyResultOfObjectAllocationIgnoredInspection()); }

  @Test
  public void testUnusedAllocationInClosure() { highlighting.get().doTest(new GroovyResultOfObjectAllocationIgnoredInspection()); }

  @Test
  public void testUsedLabel() { highlighting.get().doTest(new GroovyLabeledStatementInspection()); }

  @Test
  public void testOverlyLongMethodInspection() {
    GroovyOverlyLongMethodInspection inspection = new GroovyOverlyLongMethodInspection();
    inspection.m_limit = 5;
    highlighting.get().doTest(inspection);
  }

  @Test
  public void testRangeType() { highlighting.get().doTest(new GroovyRangeTypeCheckInspection()); }

  @Test
  public void testResolveMetaClass() { highlighting.get().doTest(); }

  @Test
  public void testResultOfAssignmentUsed() {
    GroovyResultOfAssignmentUsedInspection inspection = new GroovyResultOfAssignmentUsedInspection();
    inspection.inspectClosures = true;
    highlighting.get().doTest(inspection);
  }

  @Test
  public void testSuppressions() { highlighting.get().doTest(new GrUnresolvedAccessInspection(), new GroovyUntypedAccessInspection()); }

  @Test
  public void testInnerClassConstructorThis() {
    GroovyResultOfAssignmentUsedInspection inspection = new GroovyResultOfAssignmentUsedInspection();
    inspection.inspectClosures = true;
    highlighting.get().doTest(true, true, true, inspection);
  }

  @Test
  public void testUnnecessaryReturnInSwitch() { highlighting.get().doTest(new GroovyUnnecessaryReturnInspection()); }

  @Test
  public void testMemberShipOperatorCheck() { highlighting.get().doTest(new GroovyInArgumentCheckInspection()); }

  @Test
  public void testOctalInspection() { highlighting.get().doTest(new GroovyOctalIntegerInspection()); }

  @Test
  public void testClashingGetters() {
    highlighting.get().doTestHighlighting("""
                         class Foo {

                           boolean <warning descr="Clash occurred: 'Getter getX' with 'Getter isX'">getX</warning>() { true }
                           boolean <warning descr="Clash occurred: 'Getter isX' with 'Getter getX'">isX</warning>() { false }

                           boolean getY() {true}

                           boolean isZ() {false}

                           boolean <warning descr="Clash occurred: 'Method getFoo(int x)' with 'Getter isFoo'">getFoo</warning>(int x = 5){}
                           boolean <warning descr="Clash occurred: 'Getter isFoo' with 'Method getFoo(int x)'">isFoo</warning>(){}
                         }

                         def result = new Foo().x""", true, false, false, ClashingGettersInspection.class);
  }

  @Test
  public void testDeprecated() {
    highlighting.get().doTestHighlighting("""
                         /**
                          @deprecated
                         */
                         class X {
                           @Deprecated
                           def foo(){}

                           public static void main() {
                             new <warning descr="'X' is deprecated">X</warning>().<warning descr="'foo' is deprecated">foo</warning>()
                           }
                         }""", true, false, false, GrDeprecatedAPIUsageInspection.class);
  }

  @Test
  public void testDeprecated2() {
    highlighting.get().doTestHighlighting("""
                         class X {
                           @Deprecated
                           def X(){}

                           public static void main() {
                             new <warning descr="'X' is deprecated">X</warning>()
                           }
                         }""", true, false, false, GrDeprecatedAPIUsageInspection.class);
  }

  @Test
  public void testDeprecated3() {
    highlighting.get().doTestHighlighting("""
                         class TestService {

                             @Deprecated
                             static void test() {}

                             static void test(String s) {}

                             static void main(String[] args) {
                                 test("")
                                 <warning>test</warning>()
                             }
                         }""", true, false, false, GrDeprecatedAPIUsageInspection.class);
  }

  @Test
  public void testDeprecated4() {
    highlighting.get().doTestHighlighting("""
                         class TestService {

                             static void test() {}
                            \s
                             @Deprecated
                             static void test(String s) {}

                             static void main(String[] args) {
                                 <warning>test</warning>("")
                                 test()
                             }
                         }""", true, false, false, GrDeprecatedAPIUsageInspection.class);
  }

  @Test
  public void testSuppressedErrorInGroovyDoc() {
    highlighting.get().doTestHighlighting("""
                         class Class2 {

                           /** dependency injection for {@link GrailsFilterInvocationDefinition} */
                           @SuppressWarnings("GroovyDocCheck")
                           static main(args) {}

                           /** dependency injection for {@link <error descr="Cannot resolve symbol 'GrailsFilterInvocationDefinition'">GrailsFilterInvocationDefinition</error>} */
                           static main2(args) {}
                         }""", GroovyDocCheckInspection.class);
  }

  @Test
  public void testMissingReturnInBinaryOr() {
    highlighting.get().doTestHighlighting("""
                         private boolean onWinOrMacOS_() {
                             OperatingSystem.isWindows() || OperatingSystem.isMacOsX()
                         }
                         private boolean onWinOrMacOS() {
                             if (true) {
                                 OperatingSystem.isWindows() || OperatingSystem.isMacOsX()
                            }
                         <warning descr="Not all execution paths return a value">}</warning>

                         """, MissingReturnInspection.class);
  }

  @Test
  public void testMissingReturnInUnary() {
    highlighting.get().doTestHighlighting("""
                         boolean foo(def list) {
                           !list
                         }

                         boolean bar(def list) {
                           if (list) !list
                         <warning descr="Not all execution paths return a value">}</warning>
                         """, MissingReturnInspection.class);
  }

  @Test
  public void testMissingReturnInBinary() {
    highlighting.get().doTestHighlighting("""
                         boolean foo(def list) {
                           !list && list
                         }

                         boolean bar(def list) {
                           if (list) !list && list
                         <warning descr="Not all execution paths return a value">}</warning>
                         """, MissingReturnInspection.class);
  }

  @Test
  public void testPackageDefinition() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.addFileToProject("cde/bar.groovy", "//empty file");
    myFixture.addFileToProject("abc/foo.groovy", """
      <warning descr="Package name mismatch. Actual: 'cde', expected: 'abc'">package cde</warning>

      print 2
      """);
    myFixture.enableInspections(new GrPackageInspection());
    myFixture.testHighlighting(true, false, false, "abc/foo.groovy");
  }

  @Test
  public void testPackageDefinition2() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.addFileToProject("abc/foo.groovy", """
      <warning descr="Package name mismatch. Actual: 'cde', expected: 'abc'">package cde</warning>

      print 2
      """);
    myFixture.enableInspections(new GrPackageInspection());
    myFixture.testHighlighting(true, false, false, "abc/foo.groovy");
  }

  @Test
  public void testUntypedAccess() { highlighting.get().doTest(new GroovyUntypedAccessInspection()); }

  @Test
  public void testMethodMayBeStaticForCategoryClasses() {
    highlighting.get().doTestHighlighting("""
                         class Cat{
                           def <warning descr="Method may be static">foo</warning>() {
                               print 2
                           }
                         }

                         @groovy.lang.Category(Cat)
                         class I{
                             def foo() {
                               print 2
                             }
                         }
                         """, GrMethodMayBeStaticInspection.class);
  }

  @Test
  public void testDelegatesTo() {
    highlighting.get().doTestHighlighting("""


                         def with1(@DelegatesTo.Target() Object target, @DelegatesTo() Closure arg) { //unused
                             arg.delegate = target
                             arg()
                         }

                         def with2(@<warning descr="@Target is unused">DelegatesTo.Target</warning>('abc') Object target, @DelegatesTo() Closure arg) { //unused
                             arg.delegate = target
                             arg()
                         }

                         def with3(@DelegatesTo.Target('abc') Object target, @DelegatesTo(target='abc') Closure arg) { //unused
                             arg.delegate = target
                             arg()
                         }

                         def with4(@<warning descr="@Target is unused">DelegatesTo.Target</warning>('abcd') Object target, @DelegatesTo(target=<warning descr="Target 'abc' does not exist">'abc'</warning>) Closure arg) { //unused
                             arg.delegate = target
                             arg()
                         }

                         def with5(@<warning descr="@Target is unused">DelegatesTo.Target</warning>() Object target, @DelegatesTo(target=<warning descr="Target 'abc' does not exist">'abc'</warning>) Closure arg) { //unused
                             arg.delegate = target
                             arg()
                         }

                         def with6(@<warning descr="@Target is unused">DelegatesTo.Target</warning>() Object target, @DelegatesTo(String) Closure arg) {
                             arg.delegate = target
                             arg()
                         }

                         """, DelegatesToInspection.class);
  }

  @Test
  public void testUnnecessaryContinue() {
    highlighting.get().doTestHighlighting("""

                         for(i in []) {
                           print 2
                           <warning descr="continue is unnecessary as the last statement in a loop">continue</warning>
                         }

                         for(i in []) {
                           print 2
                           continue
                           print 3
                         }

                         for(i in []) {
                           print 2
                           switch(i) {
                             case not_last:
                               continue
                             case last:
                               <warning descr="continue is unnecessary as the last statement in a loop">continue</warning>
                           }
                         }

                         for(i in []) {
                           if (cond) {
                               print 2
                               <warning descr="continue is unnecessary as the last statement in a loop">continue</warning>
                           }
                           else {
                             continue
                             print 4
                           }
                         }

                         for (i in []) {
                           if (cond) {
                             continue
                           }
                           return
                         }

                         for (i in []) {
                           if (cond) {
                             <warning descr="continue is unnecessary as the last statement in a loop">continue</warning>
                           } else {
                             return
                           }
                         }
                         """, GroovyUnnecessaryContinueInspection.class);
  }

  @Test
  public void testEmptyCatchBlock1() {
    highlighting.get().doTestHighlighting("""

                         try{} <warning descr="Empty 'catch' block">catch</warning>(IOException e) {}
                         try{} catch(IOException ignored) {}
                         try{} catch(IOException ignore) {}
                         try{} catch(IOException e) {/*comment*/}
                         """, GroovyEmptyCatchBlockInspection.class);
  }

  @Test
  public void testEmptyCatchBlock2() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    GroovyEmptyCatchBlockInspection inspection = new GroovyEmptyCatchBlockInspection();
    inspection.myIgnore = false;
    myFixture.enableInspections(inspection);
    highlighting.get().doTestHighlighting("try{} <warning descr=\"Empty 'catch' block\">catch</warning>(IOException ignored) {}");
  }

  @Test
  public void testEmptyCatchBlock3() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    GroovyEmptyCatchBlockInspection inspection = new GroovyEmptyCatchBlockInspection();
    inspection.myIgnore = false;
    myFixture.enableInspections(inspection);
    highlighting.get().doTestHighlighting("try{} <warning descr=\"Empty 'catch' block\">catch</warning>(IOException ignored) {}");
  }

  @Test
  public void testEmptyCatchBlock4() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    GroovyEmptyCatchBlockInspection inspection = new GroovyEmptyCatchBlockInspection();
    inspection.myCountCommentsAsContent = false;
    myFixture.enableInspections(inspection);
    highlighting.get().doTestHighlighting("try{} <warning descr=\"Empty 'catch' block\">catch</warning>(IOException e) {/*comment*/}");
  }

  @Test
  public void testInvokingMethodReferenceWithDefaultParameters() { highlighting.get().doTest(new GroovyAssignabilityCheckInspection()); }
}
