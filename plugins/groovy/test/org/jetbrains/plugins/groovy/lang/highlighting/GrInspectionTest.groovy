/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.highlighting

import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyResultOfAssignmentUsedInspection
import org.jetbrains.plugins.groovy.codeInspection.bugs.*
import org.jetbrains.plugins.groovy.codeInspection.confusing.*
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialConditionalInspection
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialIfInspection
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyUnnecessaryReturnInspection
import org.jetbrains.plugins.groovy.codeInspection.declaration.GrMethodMayBeStaticInspection
import org.jetbrains.plugins.groovy.codeInspection.metrics.GroovyOverlyLongMethodInspection
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUntypedAccessInspection
/**
 * @author Max Medvedev
 */
class GrInspectionTest extends GrHighlightingTestBase {
  public void testDontSimplifyString() { doTest(new GroovyTrivialIfInspection(), new GroovyTrivialConditionalInspection()) }

  public void testSingleAllocationInClosure() {doTest(new GroovyResultOfObjectAllocationIgnoredInspection()) }

  public void testUnusedAllocationInClosure() {doTest(new GroovyResultOfObjectAllocationIgnoredInspection()) }

  public void testUsedLabel() {doTest(new GroovyLabeledStatementInspection())}

  public void testOverlyLongMethodInspection() {doTest(new GroovyOverlyLongMethodInspection())}

  public void testInaccessibleConstructorCall() { doTest(new GroovyAccessibilityInspection()) }

  public void testRangeType() { doTest(new GroovyRangeTypeCheckInspection()) }

  public void testResolveMetaClass() { doTest(new GroovyAccessibilityInspection()) }

  public void testResultOfAssignmentUsed() { doTest(new GroovyResultOfAssignmentUsedInspection()) }

  public void testSuppressions() { doTest(new GrUnresolvedAccessInspection(), new GroovyUntypedAccessInspection()) }

  public void testInnerClassConstructorThis() { doTest(true, true, true, new GroovyResultOfAssignmentUsedInspection()) }

  public void testUnnecessaryReturnInSwitch() { doTest(new GroovyUnnecessaryReturnInspection()) }

  public void testMemberShipOperatorCheck() { doTest(new GroovyInArgumentCheckInspection()) }

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

  public void testDeprecated() {
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

  public void testSuppressedErrorInGroovyDoc() {
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

  void testReassignedVarInClosureInspection() {
    addCompileStatic()
    testHighlighting("""\
test() {
    def var = "abc"
    def cl = {
        <warning descr="Local variable var is reassigned in closure with other type">var</warning> = new Date()
    }
    cl()
    var.toUpperCase()
}

test2() {
    def var = "abc"
    def cl = {
        var = 'cde'
    }
    cl()
    var.toUpperCase()
}
""", GrReassignedInClosureLocalVarInspection)
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

  public void testStaticImportProperty() {
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
import static Foo.<warning descr="Access to 'bar' exceeds its access rights">bar</warning>
import static Foo.<warning descr="Access to 'baz' exceeds its access rights">baz</warning>

print foo+<warning descr="Access to 'bar' exceeds its access rights">bar</warning>+<warning descr="Access to 'baz' exceeds its access rights">baz</warning>
''', GroovyAccessibilityInspection)
  }

  public void testUntypedAccess() { doTest(new GroovyUntypedAccessInspection()) }

  public void testMethodMayBeStaticForCategoryClasses() {
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
}
