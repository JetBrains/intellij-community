// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyResultOfAssignmentUsedInspection
import org.jetbrains.plugins.groovy.codeInspection.bugs.*
import org.jetbrains.plugins.groovy.codeInspection.confusing.*
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialConditionalInspection
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialIfInspection
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyUnnecessaryContinueInspection
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyUnnecessaryReturnInspection
import org.jetbrains.plugins.groovy.codeInspection.declaration.GrMethodMayBeStaticInspection
import org.jetbrains.plugins.groovy.codeInspection.exception.GroovyEmptyCatchBlockInspection
import org.jetbrains.plugins.groovy.codeInspection.metrics.GroovyOverlyLongMethodInspection
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUntypedAccessInspection

/**
 * @author Max Medvedev
 */
class GrInspectionTest extends GrHighlightingTestBase {
  void testDontSimplifyString() { doTest(new GroovyTrivialIfInspection(), new GroovyTrivialConditionalInspection()) }

  void testSingleAllocationInClosure() { doTest(new GroovyResultOfObjectAllocationIgnoredInspection()) }

  void testUnusedAllocationInClosure() { doTest(new GroovyResultOfObjectAllocationIgnoredInspection()) }

  void testUsedLabel() { doTest(new GroovyLabeledStatementInspection()) }

  void testOverlyLongMethodInspection() { doTest(new GroovyOverlyLongMethodInspection()) }

  void testInaccessibleConstructorCall() { doTest(new GroovyAccessibilityInspection()) }

  void testRangeType() { doTest(new GroovyRangeTypeCheckInspection()) }

  void testResolveMetaClass() { doTest(new GroovyAccessibilityInspection()) }

  void testResultOfAssignmentUsed() { doTest(new GroovyResultOfAssignmentUsedInspection(inspectClosures: true)) }

  void testSuppressions() { doTest(new GrUnresolvedAccessInspection(), new GroovyUntypedAccessInspection()) }

  void testInnerClassConstructorThis() { doTest(true, true, true, new GroovyResultOfAssignmentUsedInspection(inspectClosures: true)) }

  void testUnnecessaryReturnInSwitch() { doTest(new GroovyUnnecessaryReturnInspection()) }

  void testMemberShipOperatorCheck() { doTest(new GroovyInArgumentCheckInspection()) }

  void testOctalInspection() { doTest(new GroovyOctalIntegerInspection()) }

  void testClashingGetters() {
    testHighlighting('''\
class Foo {

  boolean <warning descr="getter 'getX' clashes with getter 'isX'">getX</warning>() { true }
  boolean <warning descr="getter 'isX' clashes with getter 'getX'">isX</warning>() { false }

  boolean getY() {true}

  boolean isZ() {false}

  boolean <warning descr="method getFoo(int x) clashes with getter 'isFoo'">getFoo</warning>(int x = 5){}
  boolean <warning descr="getter 'isFoo' clashes with method getFoo(int x)">isFoo</warning>(){}
}

def result = new Foo().x''', true, false, false, ClashingGettersInspection)
  }

  void testDeprecated() {
    testHighlighting('''\
/**
 @deprecated
*/
class X {
  @Deprecated
  def foo(){}

  public static void main() {
    new <warning descr="'X' is deprecated">X</warning>().<warning descr="'foo' is deprecated">foo</warning>()
  }
}''', true, false, false, GrDeprecatedAPIUsageInspection)
  }

  void testSuppressedErrorInGroovyDoc() {
    testHighlighting('''\
class Class2 {


  /** dependency injection for {@link GrailsFilterInvocationDefinition} */
  @SuppressWarnings("GroovyDocCheck")
  static main(args) {}

  /** dependency injection for {@link <error descr="Cannot resolve symbol 'GrailsFilterInvocationDefinition'">GrailsFilterInvocationDefinition</error>} */
  static main2(args) {}
}''', GroovyDocCheckInspection)
  }

  void testMissingReturnInBinaryOr() {
    testHighlighting('''\
private boolean onWinOrMacOS_() {
    OperatingSystem.isWindows() || OperatingSystem.isMacOsX()
}
private boolean onWinOrMacOS() {
    if (true) {
        OperatingSystem.isWindows() || OperatingSystem.isMacOsX()
   }
<warning descr="Not all execution paths return a value">}</warning>

''', MissingReturnInspection)
  }

  void testMissingReturnInUnary() {
    testHighlighting('''\
boolean foo(def list) {
  !list
}

boolean bar(def list) {
  if (list) !list
<warning descr="Not all execution paths return a value">}</warning>
''', MissingReturnInspection)
  }

  void testMissingReturnInBinary() {
    testHighlighting('''\
boolean foo(def list) {
  !list && list
}

boolean bar(def list) {
  if (list) !list && list
<warning descr="Not all execution paths return a value">}</warning>
''', MissingReturnInspection)
  }

  void testPackageDefinition() {
    myFixture.addFileToProject('cde/bar.groovy', '//empty file')
    myFixture.addFileToProject('abc/foo.groovy', '''\
<warning descr="Package name mismatch. Actual: 'cde', expected: 'abc'">package cde</warning>

print 2
''')
    myFixture.enableInspections(new GrPackageInspection())
    myFixture.testHighlighting(true, false, false, 'abc/foo.groovy')
  }

  void testPackageDefinition2() {
    myFixture.addFileToProject('abc/foo.groovy', '''\
<warning descr="Package name mismatch. Actual: 'cde', expected: 'abc'">package cde</warning>

print 2
''')
    myFixture.enableInspections(new GrPackageInspection())
    myFixture.testHighlighting(true, false, false, 'abc/foo.groovy')
  }

  void testStaticImportProperty() {
    myFixture.addFileToProject('Foo.groovy', '''\
class Foo {
  static def foo = 2
  private static def bar = 3

  private static def baz = 4

  private static def getBaz() {baz}
}
''')
    testHighlighting('''\
import static Foo.foo
import static Foo.bar
import static Foo.baz

print foo+<warning descr="Access to 'bar' exceeds its access rights">bar</warning>+<warning descr="Access to 'baz' exceeds its access rights">baz</warning>
''', GroovyAccessibilityInspection)
  }

  void testStaticImportCapsProperty() {
    myFixture.addFileToProject('Foo.groovy', '''\
class Foo {
  static def FOO = 2
  private static def BAR = 2
}
''')
    testHighlighting('''\
import static Foo.FOO
import static Foo.BAR

print FOO + <warning descr="Access to 'BAR' exceeds its access rights">BAR</warning>
''', GroovyAccessibilityInspection)
  }


  void testUntypedAccess() { doTest(new GroovyUntypedAccessInspection()) }

  void testMethodMayBeStaticForCategoryClasses() {
    testHighlighting('''\
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
''', GrMethodMayBeStaticInspection)
  }

  void testDelegatesTo() {
    testHighlighting('''

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

''', DelegatesToInspection)
  }

  void testUnnecessaryContinue() {
    testHighlighting('''
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
''', GroovyUnnecessaryContinueInspection)
  }

  void testEmptyCatchBlock1() {
    testHighlighting('''
try{} <warning descr="Empty 'catch' block">catch</warning>(IOException e) {}
try{} catch(IOException ignored) {}
try{} catch(IOException ignore) {}
try{} catch(IOException e) {/*comment*/}
''', GroovyEmptyCatchBlockInspection)
  }

  void testEmptyCatchBlock2() {
    GroovyEmptyCatchBlockInspection inspection = new GroovyEmptyCatchBlockInspection()
    inspection.myIgnore = false
    myFixture.enableInspections(inspection)
    testHighlighting('try{} <warning descr="Empty \'catch\' block">catch</warning>(IOException ignored) {}')
  }

  void testEmptyCatchBlock3() {
    GroovyEmptyCatchBlockInspection inspection = new GroovyEmptyCatchBlockInspection()
    inspection.myIgnore = false
    myFixture.enableInspections(inspection)
    testHighlighting('try{} <warning descr="Empty \'catch\' block">catch</warning>(IOException ignored) {}')
  }

  void testEmptyCatchBlock4() {
    GroovyEmptyCatchBlockInspection inspection = new GroovyEmptyCatchBlockInspection()
    inspection.myCountCommentsAsContent = false
    myFixture.enableInspections(inspection)
    testHighlighting('try{} <warning descr="Empty \'catch\' block">catch</warning>(IOException e) {/*comment*/}')
  }

  void testInvokingMethodReferenceWithDefaultParameters() { doTest(new GroovyAssignabilityCheckInspection()) }

}
