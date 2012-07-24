/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang;


import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.testFramework.IdeaTestUtil
import com.siyeh.ig.junit.JUnitAbstractTestClassNamingConventionInspection
import com.siyeh.ig.junit.JUnitTestClassNamingConventionInspection
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyResultOfAssignmentUsedInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyUncheckedAssignmentOfMemberOfRawTypeInspection
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialConditionalInspection
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialIfInspection
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyUnnecessaryReturnInspection
import org.jetbrains.plugins.groovy.codeInspection.metrics.GroovyOverlyLongMethodInspection
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection
import org.jetbrains.plugins.groovy.codeInspection.unassignedVariable.UnassignedVariableAccessInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUntypedAccessInspection
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection
import org.jetbrains.plugins.groovy.util.TestUtils
import org.jetbrains.plugins.groovy.codeInspection.bugs.*
import org.jetbrains.plugins.groovy.codeInspection.confusing.*

/**
 * @author peter
 */
public class GroovyHighlightingTest extends LightGroovyTestCase {

  final String basePath = TestUtils.testDataPath + 'highlighting/'

  public void testDuplicateClosurePrivateVariable() {
    doTest();
  }

  public void testClosureRedefiningVariable() {
    doTest();
  }

  private void doTest(InspectionProfileEntry... tools) {
    myFixture.enableInspections(tools);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".groovy");
  }

  public void testCircularInheritance() {
    doTest();
  }

  public void testEmptyTupleType() {
    doTest();
  }

  public void testMapDeclaration() {
    doTest();
  }

  public void testShouldntImplementGroovyObjectMethods() {
    addGroovyObject();
    myFixture.addFileToProject("Foo.groovy", "class Foo {}");
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java");
  }

  public void testJavaClassImplementingGroovyInterface() {
    addGroovyObject();
    myFixture.addFileToProject("Foo.groovy", "interface Foo {}");
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java");
  }

  public void testDuplicateFields() {
    doTest();
  }

  public void testNoDuplicationThroughClosureBorder() {
    myFixture.addClass("package groovy.lang; public interface Closure {}");
    doTest();
  }

  public void testRecursiveMethodTypeInference() {
    doTest();
  }

  public void testSuperClassNotExists() {
    doTest();
  }
  public void testDontSimplifyString() { doTest(new GroovyTrivialIfInspection(), new GroovyTrivialConditionalInspection()); }

  public void testRawMethodAccess() { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawFieldAccess() { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawArrayStyleAccess() { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawArrayStyleAccessToMap() { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawArrayStyleAccessToList() { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testIncompatibleTypesAssignments() { doTest(new GroovyAssignabilityCheckInspection()); }

  public void testAnonymousClassConstructor() {doTest();}
  public void testAnonymousClassAbstractMethod() {doTest();}
  public void testAnonymousClassStaticMethod() {doTest();}
  public void testAnonymousClassShoudImplementMethods() {doTest();}
  public void testAnonymousClassShouldImplementSubstitutedMethod() {doTest();}

  public void testDefaultMapConstructorNamedArgs() {
    doTest(new GroovyConstructorNamedArgumentsInspection(), new GroovyAssignabilityCheckInspection());
  }
  public void testDefaultMapConstructorNamedArgsError() {
    doTest(new GroovyConstructorNamedArgumentsInspection(), new GroovyAssignabilityCheckInspection());
  }
  public void testDefaultMapConstructorWhenDefConstructorExists() {
    doTest(new GroovyConstructorNamedArgumentsInspection(), new GroovyAssignabilityCheckInspection());
  }

  public void testSingleAllocationInClosure() {doTest(new GroovyResultOfObjectAllocationIgnoredInspection());}
  public void testUnusedAllocationInClosure() {doTest(new GroovyResultOfObjectAllocationIgnoredInspection());}

  public void testUnresolvedLhsAssignment() { doTest(new GroovyUnresolvedAccessInspection()); }

  public void testUnresolvedMethodCallWithTwoDeclarations() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testUnresolvedAccess() { doTest(new GroovyUnresolvedAccessInspection()); }
  public void testBooleanProperties() { doTest(new GroovyUnresolvedAccessInspection()); }
  public void testUntypedAccess() { doTest(new GroovyUntypedAccessInspection()); }

  public void testUnassigned1() { doTest(new UnassignedVariableAccessInspection()); }
  public void testUnassigned2() { doTest(new UnassignedVariableAccessInspection()); }
  public void testUnassigned3() { doTest(new UnassignedVariableAccessInspection()); }
  public void testUnassigned4() { doTest(new UnassignedVariableAccessInspection()); }
  public void testUnassignedTryFinally() { doTest(new UnassignedVariableAccessInspection()); }

  public void testUnusedVariable() { doTest(new UnusedDefInspection(), new GrUnusedIncDecInspection()); }
  public void testDefinitionUsedInClosure() { doTest(new UnusedDefInspection(), new GrUnusedIncDecInspection()); }
  public void testDefinitionUsedInClosure2() { doTest(new UnusedDefInspection(), new GrUnusedIncDecInspection()); }
  public void testDefinitionUsedInSwitchCase() { doTest(new UnusedDefInspection(), new GrUnusedIncDecInspection()); }
  public void testDuplicateInnerClass() {doTest();}

  public void testThisInStaticContext() {doTest();}
  public void testLocalVariableInStaticContext() {doTest();}

  public void testModifiersInPackageAndImportStatements() {
    myFixture.copyFileToProject(getTestName(false) + ".groovy", "x/"+getTestName(false)+".groovy");
    myFixture.testHighlighting(true, false, false, "x/"+getTestName(false)+".groovy");
  }

  public void testBreakOutside() {doTest();}
  public void testUndefinedLabel() {doTest();}
  public void testUsedLabel() {doTest(new GroovyLabeledStatementInspection());}

  public void testNestedMethods() {
    doTest();
  }

  public void testRawOverridedMethod() {doTest();}

  public void testFQNJavaClassesUsages() {
    doTest();
  }

  public void testGstringAssignableToString() {doTest();}
  public void testGstringAssignableToStringInClosureParameter() {doTest();}
  public void testEverythingAssignableToString() {doTest(new GroovyAssignabilityCheckInspection());}

  public void testEachOverRange() {doTest();}

  public void testEllipsisParam() {
    myFixture.configureByText('a.groovy', '''\
class A {
  def foo(int... x){}
  def foo(<error descr="Ellipsis type is not allowed here">int...</error> x, double y) {}
}
''')
    myFixture.checkHighlighting(true, false, false)
  }

  public void testMethodCallWithDefaultParameters() {doTest(new GroovyAssignabilityCheckInspection());}
  public void testClosureWithDefaultParameters() {doTest(new GroovyAssignabilityCheckInspection());}
  public void testClosureCallMethodWithInapplicableArguments() {doTest(new GroovyAssignabilityCheckInspection());}
  public void testCallIsNotApplicable() {doTest(new GroovyAssignabilityCheckInspection());}
  public void testPathCallIsNotApplicable() {doTest(new GroovyAssignabilityCheckInspection());}

  public void testOverlyLongMethodInspection() {
    doTest(new GroovyOverlyLongMethodInspection());
  }

  public void testStringAndGStringUpperBound() {doTest();}

  public void testWithMethod() {doTest();}
  public void testByteArrayArgument() {doTest(new GroovyAssignabilityCheckInspection());}

  public void testForLoopWithNestedEndlessLoop() {doTest(new UnassignedVariableAccessInspection());}
  public void testPrefixIncrementCfa() {doTest(new UnusedDefInspection(), new GrUnusedIncDecInspection());}
  public void testIfIncrementElseReturn() {doTest(new UnusedDefInspection(), new GrUnusedIncDecInspection()); }

  public void testArrayLikeAccess() {doTest();}

  public void testSetInitializing() {doTest();}

  public void testEmptyTupleAssignability() {doTest();}

  public void testGrDefFieldsArePrivateInJavaCode() {
    myFixture.configureByText("X.groovy", "public class X{def x=5}");
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testSuperConstructorInvocation() {doTest();}

  public void testDuplicateMapKeys() {doTest();}

  public void testIndexPropertyAccess() {doTest();}

  public void testPropertyAndFieldDeclaration() {doTest();}

  public void testGenericsMethodUsage() {doTest();}

  public void testWildcardInExtendsList() {doTest();}

  public void testOverrideAnnotation() {doTest();}

  public void testClosureCallWithTupleTypeArgument() {doTest();}

  public void testMethodDuplicates() {doTest();}

  public void testPutValueToEmptyMap() {doTest(new GroovyAssignabilityCheckInspection());}
  public void testPutIncorrectValueToMap() {doTest(new GroovyAssignabilityCheckInspection());}

  public void testAmbiguousCodeBlock() {doTest();}
  public void testAmbiguousCodeBlockInMethodCall() {doTest();}
  public void testNotAmbiguousClosableBlock() {doTest();}
  public void testDuplicateParameterInClosableBlock() {doTest();}

  public void testCyclicInheritance() {doTest();}

  public void testNoDefaultConstructor() {doTest();}

  public void testTupleTypeAssignments() {doTest(new GroovyAssignabilityCheckInspection());}

  public void testInaccessibleConstructorCall() {
    doTest(new GroovyAccessibilityInspection());
  }

  public void testSignatureIsNotApplicableToList() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testInheritConstructorsAnnotation() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testCollectionAssignments() {doTest(new GroovyAssignabilityCheckInspection()); }
  public void testReturnAssignability() {doTest(new GroovyAssignabilityCheckInspection()); }

  public void testNumberDuplicatesInMaps() {doTest();}

  public void testMapNotAcceptedAsStringParameter() {doTest(new GroovyAssignabilityCheckInspection());}

  public void testBuiltInTypeInstantiation() {doTest();}

  public void testSwitchControlFlow() {doTest(new UnusedDefInspection(), new GroovyResultOfAssignmentUsedInspection(), new GrUnusedIncDecInspection());}

  public void testRawTypeInAssignment() {doTest(new GroovyAssignabilityCheckInspection());}

  public void testSOEInFieldDeclarations() {doTest();}

  public void testVeryLongDfaWithComplexGenerics() {
    IdeaTestUtil.assertTiming("", 10000, 1, new Runnable() {
      @Override
      public void run() {
        doTest(new GroovyAssignabilityCheckInspection(), new UnusedDefInspection(), new GrUnusedIncDecInspection());
      }
    });
  }

  public void testWrongAnnotation() {doTest();}

  public void testAmbiguousMethods() {
    myFixture.copyFileToProject(getTestName(false) + ".java");
    doTest();
  }

  public void testMapParamWithNoArgs() {doTest(new GroovyAssignabilityCheckInspection());}

  public void testGroovyEnumInJavaFile() {
    myFixture.copyFileToProject(getTestName(false) + ".groovy");
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testRangeType() {
    doTest(new GroovyRangeTypeCheckInspection());
  }

  public void testResolveMetaClass() {
    doTest(new GroovyAccessibilityInspection());
  }

  public void testSOFInDelegate() {
    doTest();
  }

  public void testInheritInterfaceInDelegate() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testMethodImplementedByDelegate() {
    doTest();
  }

  public void testVarNotAssigned() {
    doTest(new UnassignedVariableAccessInspection());
  }

  public void testMultipleVarNotAssigned() {
    doTest(new UnassignedVariableAccessInspection());
  }

  public void testTestMarkupStubs() {
    doTest();
  }

  public void testResultOfAssignmentUsed() {
    doTest(new GroovyResultOfAssignmentUsedInspection());
  }

  public void testGdslWildcardTypes() {
    myFixture.configureByText("a.groovy",
                              "List<? extends String> la = []; la.get(1); " +
                              "List<? super String> lb = []; lb.get(1); " +
                              "List<?> lc = []; lc.get(1); "
    );
    myFixture.checkHighlighting(true, false, false);
  }

  public void testThisTypeInStaticContext() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testSuppressions() {
    doTest(new GroovyUnresolvedAccessInspection(), new GroovyUntypedAccessInspection());
  }

  public void testUsageInInjection() { doTest(new UnusedDefInspection(), new GrUnusedIncDecInspection()); }

  public void testDuplicatedNamedArgs() {doTest();}

  public void testAnonymousClassArgList() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testConstructorWithAllParametersOptional() {
    doTest();
  }

  public void testTupleConstructorAttributes() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testCanonicalConstructorApplicability() {
    myFixture.addClass("package groovy.transform; public @interface Canonical {}");
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testUnusedDefsForArgs() {
    doTest(new UnusedDefInspection(), new GrUnusedIncDecInspection());
  }

  public void testUsedDefBeforeTry1() {
    doTest(new UnusedDefInspection(), new GrUnusedIncDecInspection());
  }

  public void testUsedDefBeforeTry2() {
    doTest(new UnusedDefInspection(), new GrUnusedIncDecInspection());
  }

  public void testUnusedInc() {
    doTest(new UnusedDefInspection(), new GrUnusedIncDecInspection())
  }

  public void testUsedInCatch() {
    doTest(new UnusedDefInspection())
  }

  public void testStringAssignableToChar() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testInnerClassConstructorThis() {
    myFixture.enableInspections(new GroovyResultOfAssignmentUsedInspection());
    myFixture.testHighlighting(true, true, true, getTestName(false) + ".groovy");
  }

  public void testCurrying(){
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testAnotherCurrying(){
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testResultOfIncUsed() {
    doTest(new GroovyResultOfIncrementOrDecrementUsedInspection());
  }

  public void testNativeMapAssignability() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testDelegatedMethodIsImplemented() {
    doTest();
  }

  public void testEnumImplementsAllGroovyObjectMethods() {
    doTest();
  }

  public void testTwoLevelGrMap() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testPassingCollectionSubtractionIntoGenericMethod() {
    doTest(new GroovyAssignabilityCheckInspection(), new GroovyUnresolvedAccessInspection());
  }

  public void testBuilderMembersAreNotUnresolved() {
    doTest(new GroovyUnresolvedAccessInspection());
  }

  public void testImplicitEnumCoercion() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testUnknownVarInArgList() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testCallableProperty() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testConstructor() {
    doTest(new GroovyAssignabilityCheckInspection(), new GroovyConstructorNamedArgumentsInspection());
  }

  public void testRecursiveConstructors() {
    doTest();
  }

  public void testEnumConstantConstructors() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testUnnecessaryReturnInSwitch() {
    doTest(new GroovyUnnecessaryReturnInspection());
  }

  public void testLiteralConstructorUsages() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testSpreadArguments() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testImmutableConstructorFromJava() {
    myFixture.addFileToProject "a.groovy", '''@groovy.transform.Immutable class Foo { int a; String b }'''
    myFixture.configureByText 'a.java', '''
class Bar {{
  new Foo<error>()</error>;
  new Foo<error>(2)</error>;
  new Foo(2, "3");
}}'''
    myFixture.checkHighlighting(false, false, false)
  }

  public void testTupleConstructorFromJava() {
    myFixture.addFileToProject "a.groovy", '''@groovy.transform.TupleConstructor class Foo { int a; String b }'''
    myFixture.configureByText 'a.java', '''
class Bar {{
  new Foo();
  new Foo(2);
  new Foo(2, "3");
  new Foo<error>(2, "3", 9)</error>;
}}'''
    myFixture.checkHighlighting(false, false, false)
  }

  public void testInheritConstructorsFromJava() {
    myFixture.addFileToProject "a.groovy", '''
class Person {
  Person(String first, String last) { }
  Person(String first, String last, String address) { }
  Person(String first, String last, int zip) { }
}

@groovy.transform.InheritConstructors
class PersonAge extends Person {
  PersonAge(String first, String last, int zip) { }
}
'''
    myFixture.configureByText 'a.java', '''
class Bar {{
  new PersonAge("a", "b");
  new PersonAge("a", "b", "c");
  new PersonAge("a", "b", 239);
  new PersonAge<error>(2, "3", 9)</error>;
}}'''
    myFixture.checkHighlighting(false, false, false)
  }

  public void testDiamondTypeInferenceSOE() {
    myFixture.configureByText 'a.groovy', ''' Map<Integer, String> a; a[2] = [:] '''
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection())
    myFixture.checkHighlighting(false, false, false)
  }

  public void testMemberShipOperatorCheck() {
    doTest(new GroovyInArgumentCheckInspection());
  }

  void testDefaultInitializersAreNotAllowedInAbstractMethods() {doTest()}
  void testConstructorTypeArgs(){doTest()}

  void testIncorrectEscaping() {doTest()}

  void testRegexInCommandArg() {doTest()}
  void testOctalInspection() {
    doTest(new GroovyOctalIntegerInspection())
  }

  void testThisInStaticMethodOfAnonymousClass() {
    myFixture.configureByText('a.groovy', '''\
class A {
    static abc
    def foo() {
        new Runnable() {
            <error descr="Inner classes cannot have static declarations">static</error> void run() {
                print abc
            }
        }.run()
    }
}''')

    myFixture.enableInspections(GroovyAssignabilityCheckInspection)
    myFixture.checkHighlighting(true, false, false);
  }

  public void testJUnitConvention() {
    myFixture.addClass("package junit.framework; public class TestCase {}")
    doTest(new JUnitTestClassNamingConventionInspection(), new JUnitAbstractTestClassNamingConventionInspection())
  }

  void testDuplicateMethods() {
    myFixture.configureByText('a.groovy', '''\
class A {
  <error descr="Method with signature foo() is already defined in the class 'A'">def foo()</error>{}
  <error descr="Method with signature foo() is already defined in the class 'A'">def foo(def a=null)</error>{}
}
''')
    myFixture.checkHighlighting(true, false, false)
  }

  void testPrivateTopLevelClassInJava() {
    myFixture.addFileToProject('pack/Foo.groovy', 'package pack; private class Foo{}')
    myFixture.configureByText('Abc.java', '''\
import pack.<error descr="'pack.Foo' is not public in 'pack'. Cannot be accessed from outside package">Foo</error>;

class Abc {
  void foo() {
    System.out.print(new <error descr="'pack.Foo' is not public in 'pack'. Cannot be accessed from outside package">Foo</error>());
  }
}''')

    myFixture.testHighlighting(false, false, false)
  }

  void testDelegateToMethodWithItsOwnTypeParams() {
    myFixture.configureByText('a.groovy', '''\
interface I<S> {
    def <T> void foo(List<T> a);
}

class Foo {
    @Delegate private I list
}

<error descr="Method 'foo' is not implemented">class Bar implements I</error> {
  def <T> void foo(List<T> a){}
}

class Baz implements I {
  def void foo(List a){}
}
''')

    myFixture.testHighlighting(false, false, false)
  }

  void testClashingGetters() {
    myFixture.configureByText('a.groovy', '''\
class Foo {

  boolean <warning descr="getter 'getX' clashes with getter 'isX'">getX</warning>() { true }
  boolean <warning descr="getter 'isX' clashes with getter 'getX'">isX</warning>() { false }

  boolean getY() {true}

  boolean isZ() {false}

  boolean <warning descr="method getFoo(int x) clashes with getter 'isFoo'">getFoo</warning>(int x = 5){}
  boolean <warning descr="getter 'isFoo' clashes with method getFoo(int x)">isFoo</warning>(){}
}

def result = new Foo().x''')
    myFixture.enableInspections(new ClashingGettersInspection())
    myFixture.testHighlighting(true, false, false)
  }

  void testPrimitiveTypeParams() {
    myFixture.configureByText('a.groovy', '''\
List<<error descr="Primitive type parameters are not allowed in type parameter list">int</error>> list = new ArrayList<int><EOLError descr="'(' expected"></EOLError>
List<? extends <error descr="Primitive bound types are not allowed">double</error>> l = new ArrayList<double>()
List<?> list2
''')
    myFixture.testHighlighting(true, false, false)
  }

  public void testGloballyUnusedSymbols() {
    doTest(new GroovyUnusedDeclarationInspection(), new UnusedDeclarationInspection())
  }

  public void testGloballyUnusedInnerMethods() {
    myFixture.addClass 'package junit.framework; public class TestCase {}'
    doTest(new GroovyUnusedDeclarationInspection(), new UnusedDeclarationInspection())
  }

  public void testUnusedParameter() {
    doTest(new GroovyUnusedDeclarationInspection(), new UnusedDeclarationInspection())
  }

  public void testAliasInParameterType() {
    myFixture.configureByText('a_.groovy', '''\
import java.awt.event.ActionListener
import java.awt.event.ActionEvent as AE

public class CorrectImplementor implements ActionListener {
  public void actionPerformed (AE e) { //AE is alias to ActionEvent
  }
}

<error descr="Method 'actionPerformed' is not implemented">public class IncorrectImplementor implements ActionListener</error> {
  public void actionPerformed (Object e) {
  }
}
''')
    myFixture.testHighlighting(true, false, false)
  }

  public void testReassignedHighlighting() {
    myFixture.testHighlighting(true, true, true, getTestName(false) + ".groovy");
  }

  public void testDeprecated() {
    myFixture.configureByText('_a.groovy', '''\
/**
 @deprecated
*/
class X {
  @Deprecated
  def foo(){}

  public static void main() {
    new <warning descr="'X' is deprecated">X</warning>().<warning descr="'foo' is deprecated">foo</warning>()
  }
}''')

    myFixture.enableInspections(GrDeprecatedAPIUsageInspection)
    myFixture.testHighlighting(true, false, false)
  }

  public void testInstanceOf() {
    myFixture.configureByText('_a.groovy', '''\
class DslPointcut {}

private def handleImplicitBind(arg) {
    if (arg instanceof Map && arg.size() == 1 && arg.keySet().iterator().next() instanceof String && arg.values().iterator().next() instanceof DslPointcut) {
        return DslPointcut.bind(arg)
    }
    return arg
}''')
    myFixture.testHighlighting(true, false, false)
  }

  public void testTargetAnnotationInsideGroovy1() {
    myFixture.addFileToProject('Ann.groovy', '''
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.*

@Target(FIELD)
@interface Ann {}
''')

    myFixture.configureByText('_.groovy', '''\
@<error descr="'@Ann' not applicable to type">Ann</error>
class C {
  @Ann
  def foo

  def ar() {
    @<error descr="'@Ann' not applicable to local variable">Ann</error>
    def x
  }
}''')

    myFixture.testHighlighting(true, false, false)
  }

  public void testTargetAnnotationInsideGroovy2() {
    myFixture.addFileToProject('Ann.groovy', '''
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.*

@Target(value=[FIELD, TYPE])
@interface Ann {}
''')

    myFixture.configureByText('_.groovy', '''\
@Ann
class C {
  @Ann
  def foo

  def ar() {
    @<error descr="'@Ann' not applicable to local variable">Ann</error>
    def x
  }
}''')
    myFixture.testHighlighting(true, false, false)
  }

  public void testTargetAnnotationInsideGroovy3() {
    myFixture.addFileToProject('Ann.groovy', '''
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.*

@Target(LOCAL_VARIABLE)
@interface Ann {}
''')

    myFixture.configureByText('_.groovy', '''\
@<error descr="'@Ann' not applicable to type">Ann</error>
class C {
  @<error descr="'@Ann' not applicable to field">Ann</error>
  def foo

  def ar() {
    @Ann
    def x
  }
}''')
    myFixture.testHighlighting(true, false, false)
  }


  public void testNonInferrableArgsOfDefParams() {
    myFixture.configureByText('_.groovy', '''\
def foo0(def a) { }
def bar0(def b) { foo0(b) }

def foo1(Object a) { }
def bar1(def b) { foo1(b) }

def foo2(String a) { }
def bar2(def b) { foo2<weak_warning descr="Cannot infer argument types">(b)</weak_warning> }
''')
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection())
    myFixture.testHighlighting(true, false, true)
  }

  public void testPutAtApplicability() {
    myFixture.addClass("""\
package java.util;
public class LinkedHashMap<K,V> extends HashMap<K,V> implements Map<K,V> {}
""")

    myFixture.configureByText('_.groovy', '''\
LinkedHashMap<File, List<File>> files = [:]
files[new File('a')] = [new File('b')]
files<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '(java.io.File, java.io.File)'">[new File('a')]</warning> = new File('b')
''')
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection())
    myFixture.testHighlighting(true, false, true)
  }

  public void testStringToCharAssignability() {
    myFixture.configureByText('_.groovy', '''\
def foo(char c){}

foo<warning descr="'foo' in '_' cannot be applied to '(java.lang.String)'">('a')</warning>
foo('a' as char)
foo('a' as Character)

char c = 'a'
''')
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection())
    myFixture.testHighlighting(true, false, true)
  }

  public void testSuppressedErrorInGroovyDoc() {
    myFixture.configureByText('_.groovy', '''\
class Class2 {


  /** dependency injection for {@link GrailsFilterInvocationDefinition} */
  @SuppressWarnings("GroovyDocCheck")
  static main(args) {}

  /** dependency injection for {@link <error descr="Cannot resolve symbol 'GrailsFilterInvocationDefinition'">GrailsFilterInvocationDefinition</error>} */
  static main2(args) {}
}''')
    myFixture.enableInspections(new GroovyDocCheckInspection())
    myFixture.testHighlighting(true, false, true)
  }

  public void testIncorrectTypeArguments(){
    myFixture.configureByText('_.groovy', '''\
class C <T extends String> {}
C<<error descr="Type parameter 'java.lang.Double' is not in its bound; should extend 'java.lang.String'">Double</error>> c
C<String> c2
C<error descr="Wrong number of type arguments: 2; required: 1"><String, Double></error> c3
''')
    myFixture.testHighlighting(true, false, true)
  }

  public void testRawClosureReturnType() {
    testHighlighting('''\
class A<T> {
  A(T t) {this.t = t}

  T t
  def cl = {
    return t
  }
}


def a = new A(new Date())
Date d = <warning descr="Cannot assign 'Object' to 'Date'">a.cl()</warning>
''', GroovyUncheckedAssignmentOfMemberOfRawTypeInspection)
  }

  private void testHighlighting(String text, Class<? extends LocalInspectionTool>... inspections) {
    myFixture.configureByText('_.groovy', text)
    myFixture.enableInspections(inspections)
    myFixture.testHighlighting(true, false, true)
  }

  void testMethodRefs1() {
    testHighlighting('''\
class A {
  int foo(){2}

  Date foo(int x) {null}
}

def foo = new A().&foo

int i = foo()
int i2 = <warning descr="Cannot assign 'Date' to 'int'">foo(2)</warning>
Date d = foo(2)
Date d2 = <warning descr="Cannot assign 'Integer' to 'Date'">foo()</warning>
''', GroovyAssignabilityCheckInspection)
  }

  void testMethodRefs2() {
    testHighlighting('''\
class Bar {
  def foo(int i, String s2) {s2}
  def foo(int i, int i2) {i2}
}

def cl = new Bar<error descr="'(' expected">.</error>&foo
cl = cl.curry(1)
String s = cl("2")
int s2 = <warning descr="Cannot assign 'String' to 'int'">cl("2")</warning>
int i = cl(3)
String i2 = cl(3)
''', GroovyAssignabilityCheckInspection)
  }

  void testThrowObject() {
    testHighlighting('''\
def foo() {
  throw new RuntimeException()
}
def bar () {
  throw <warning descr="Cannot assign 'Object' to 'Throwable'">new Object()</warning>
}

def test() {
  throw new Throwable()
}
''', GroovyAssignabilityCheckInspection)
  }

  void testTryCatch1() {
    testHighlighting('''\
try {}
catch (Exception e){}
catch (<warning descr="Exception 'java.io.IOException' has already been caught">IOException</warning> e){}
''')
  }

  void testTryCatch2() {
    testHighlighting('''\
try {}
catch (e){}
catch (<warning descr="Exception 'java.lang.Throwable' has already been caught">e</warning>){}
''')
  }

  void testTryCatch3() {
    testHighlighting('''\
try {}
catch (e){}
catch (<warning descr="Exception 'java.io.IOException' has already been caught">IOException</warning> e){}
''')
  }

  void testTryCatch4() {
    testHighlighting('''\
try {}
catch (Exception | <warning descr="Unnecessary exception 'java.io.IOException'. 'java.lang.Exception' is already declared">IOException</warning> e){}
''')
  }

  void testTryCatch5() {
    testHighlighting('''\
try {}
catch (RuntimeException | IOException e){}
catch (<warning descr="Exception 'java.lang.NullPointerException' has already been caught">NullPointerException</warning> e){}
''')
  }

  void testTryCatch6() {
    testHighlighting('''\
try {}
catch (NullPointerException | IOException e){}
catch (ClassNotFoundException | <warning descr="Exception 'java.lang.NullPointerException' has already been caught">NullPointerException</warning> e){}
''')
  }

  void testCategoryWithPrimitiveType() {
    testHighlighting('''\
class Cat {
  static foo(Integer x) {}
}

use(Cat) {
  1.with {
    foo()
  }

  (1 as int).foo()
}

class Ca {
  static foo(int x) {}
}

use(Ca) {
  1.<warning descr="Category method 'foo' cannot be applied to 'java.lang.Integer'">foo</warning>()
  (1 as int).<warning descr="Category method 'foo' cannot be applied to 'java.lang.Integer'">foo</warning>()
}
''', GroovyAssignabilityCheckInspection)
  }

  void testCompileStatic() {
    myFixture.addClass('''\
package groovy.transform;
public @interface CompileStatic {
}''')

    myFixture.configureByText('_.groovy', '''\
<info descr="null">import</info> <info descr="null">groovy.transform.CompileStatic</info>

<info descr="null">class</info> A {

<info descr="null">def</info> <info descr="null">foo</info>() {
<info descr="null">print</info> <info descr="null">abc</info>
}

<info descr="null">@CompileStatic</info>
<info descr="null">def</info> bar() {
<info descr="null">print</info> <error descr="Cannot resolve symbol 'abc'">abc</error>
}
}
''')
    myFixture.testHighlighting(true, false, false)
  }


  void testCompileStaticWithAssignabilityCheck() {
    myFixture.addClass('''\
package groovy.transform;
public @interface CompileStatic {
}''')

    myFixture.configureByText('_.groovy', '''\
import groovy.transform.CompileStatic

class A {

  def foo(String s) {
    int x = <warning descr="Cannot assign 'Date' to 'int'">new Date()</warning>
  }

  @CompileStatic
  def bar() {
    int x = <error descr="Cannot assign 'Date' to 'int'">new Date()</error>
  }
}
''')
    myFixture.enableInspections(GroovyAssignabilityCheckInspection)
    myFixture.checkHighlighting(true, false, true)
  }

  void testUsedVar() {
    testHighlighting('''\
def foo(xxx) {
  if ((xxx = 5) || xxx) {
    <warning descr="Assignment is not used">xxx</warning>=4
  }
}

def foxo(doo) {
  def xxx = 'asdf'
  if (!doo) {
    println xxx
    <warning descr="Assignment is not used">xxx</warning>=5
  }
}

def bar(xxx) {
  print ((xxx=5)?:xxx)
}

def a(xxx) {
  if (2 && (xxx=5)) {
    xxx
  }
  else {
  }
}
''', UnusedDefInspection)
  }

  void testUnresolvedVarInStaticMethod() {
    testHighlighting('''\
static def foo() {
  print <error descr="Cannot resolve symbol 'abc'">abc</error>

  def cl = {
     print cde
  }
}
''')
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

  void testScriptFieldsAreAllowedOnlyInScriptBody() {
    addGroovyTransformField()
    testHighlighting('''\
import groovy.transform.Field

@Field
def foo

def foo() {
  <error descr="Annotation @Field can only be used within a script body">@Field</error>
  def bar
}

class X {
  <error descr="Annotation @Field can only be used within a script">@Field</error>
  def bar

  def b() {
    <error descr="Annotation @Field can only be used within a script">@Field</error>
    def x
  }
}
''')
  }

  void testDuplicatedScriptField() {
    addGroovyTransformField()
    testHighlighting('''\
import groovy.transform.Field

while(true) {
  @Field def <error descr="Field 'foo' already defined">foo</error>
}

while(false) {
  @Field def <error descr="Field 'foo' already defined">foo</error>
}

while(i) {
  def foo
}

def foo
''')
  }

  void testReturnTypeInStaticallyCompiledMethod() {
   addCompileStatic();
   testHighlighting('''\
import groovy.transform.CompileStatic
@CompileStatic
int method(x, y, z) {
    if (x) {
        <error descr="Cannot assign 'String' to 'int'">'String'</error>
    } else if (y) {
        42
    }
    else if (z) {
      return <error descr="Cannot assign 'String' to 'int'">'abc'</error>
    }
    else {
      return 43
    }
}
''')
 }

  void testReassignedVarInClosure() {
    addCompileStatic()
    testHighlighting("""
$IMPORT_COMPILE_STATIC

@CompileStatic
test() {
    def var = "abc"
    def cl = {
        var = new Date()
    }
    cl()
    var.<error descr="Cannot resolve symbol 'toUpperCase'">toUpperCase</error>()
}
""")
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

  void testOverrideForVars() {
    testHighlighting('''\
class S {
  @<error descr="'@Override' not applicable to field">Override</error> def foo;

  def bar() {
   @<error descr="'@Override' not applicable to local variable">Override</error> def x
  }
}''')
  }

  void testUnusedImportToList() {
    myFixture.addClass('''package java.awt; public class Component{}''')
    testHighlighting('''\
import java.awt.Component
<warning descr="Unused import">import java.util.List</warning>

print Component
print List
''')
  }

  void testUsedImportToList() {
    myFixture.addClass('''package java.awt; public class Component{}''')
    myFixture.addClass('''package java.awt; public class List{}''')
    myFixture.addClass('''package java.util.concurrent; public class ConcurrentHashMap{}''')
    testHighlighting('''\
import java.awt.*
import java.util.List
<warning descr="Unused import">import java.util.concurrent.ConcurrentHashMap</warning>

print Component
print List
''')
  }
}