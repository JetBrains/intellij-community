// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.testFramework.UsefulTestCase;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.jetbrains.plugins.groovy.util.ThrowingTransformation;

public class ResolvePropertyTest extends GroovyResolveTestCase {
  public void testParameter1() {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    resolve("A.groovy", GrParameter.class);
  }

  public void testClosureParameter1() {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    resolve("A.groovy", GrParameter.class);
  }

  public void testClosureOwner() {
    PsiReference ref = configureByFile("closureOwner/A.groovy");
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testLocal1() throws Exception {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    doTest("local1/A.groovy");
  }

  public void testField1() throws Exception {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    doTest("field1/A.groovy");
  }

  public void testField2() throws Exception {
    doTest("field2/A.groovy");
  }

  public void testArrayLength() throws Exception {
    doTest("arrayLength/A.groovy");
  }

  public void testFromGetter() {
    PsiReference ref = configureByFile("fromGetter/A.groovy");
    TestCase.assertTrue(ref.resolve() instanceof GrAccessorMethod);
  }

  public void testFromGetter2() {
    PsiReference ref = configureByFile("fromGetter2/A.groovy");
    TestCase.assertTrue(ref.resolve() instanceof GrAccessorMethod);
  }

  public void testFromSetter2() {
    PsiReference ref = configureByFile("fromSetter2/A.groovy");
    TestCase.assertTrue(ref.resolve() instanceof GrAccessorMethod);
  }

  public void testFromSetter() {
    PsiReference ref = configureByFile("fromSetter/A.groovy");
    TestCase.assertTrue(ref.resolve() instanceof GrAccessorMethod);
  }

  public void testCatchParameter() {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    resolve("CatchParameter.groovy", GrParameter.class);
  }

  public void testCaseClause() throws Exception {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    doTest("caseClause/CaseClause.groovy");
  }

  public void testGrvy104() throws Exception {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    doTest("grvy104/Test.groovy");
  }

  public void testGrvy270() {
    PsiReference ref = configureByFile("grvy270/Test.groovy");
    TestCase.assertNull(ref.resolve());
  }

  public void testGrvy1483() {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    resolve("Test.groovy", GrVariable.class);
  }

  public void testField3() {
    GrReferenceElement ref = (GrReferenceElement)configureByFile("field3/A.groovy").getElement();
    GroovyResolveResult resolveResult = ref.advancedResolve();
    TestCase.assertTrue(resolveResult.getElement() instanceof GrField);
    TestCase.assertFalse(resolveResult.isValidResult());
  }

  public void testToGetter() {
    GrReferenceElement ref = (GrReferenceElement)configureByFile("toGetter/A.groovy").getElement();
    PsiElement resolved = ref.resolve();
    TestCase.assertTrue(resolved instanceof GrMethod);
    TestCase.assertTrue(PropertyUtil.isSimplePropertyGetter((PsiMethod)resolved));
  }

  public void testToSetter() {
    GrReferenceElement ref = (GrReferenceElement)configureByFile("toSetter/A.groovy").getElement();
    PsiElement resolved = ref.resolve();
    TestCase.assertTrue(resolved instanceof GrMethod);
    TestCase.assertTrue(PropertyUtil.isSimplePropertySetter((PsiMethod)resolved));
  }

  public void testUndefinedVar1() {
    PsiReference ref = configureByFile("undefinedVar1/A.groovy");
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrBindingVariable.class);
  }

  public void testRecursive1() {
    PsiReference ref = configureByFile("recursive1/A.groovy");
    PsiElement resolved = ref.resolve();
    TestCase.assertTrue(resolved instanceof GrField);
  }

  public void testRecursive2() {
    PsiReference ref = configureByFile("recursive2/A.groovy");
    PsiElement resolved = ref.resolve();
    TestCase.assertTrue(resolved instanceof GrMethod);
    TestCase.assertEquals(CommonClassNames.JAVA_LANG_OBJECT, ((GrMethod)resolved).getReturnType().getCanonicalText());
  }

  public void testUndefinedVar2() throws Exception {
    doUndefinedVarTest("undefinedVar2/A.groovy");
  }

  public void testUndefinedVar3() {
    resolveByText("""
                    
                      (aa, b) = [1, 4]
                      c = a<caret>a
                     \s\
                    """, GrBindingVariable.class);
  }

  public void testDefinedVar1() {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    resolve("A.groovy", GrVariable.class);
  }

  public void testOperatorOverload() throws Exception {
    doTest("operatorOverload/A.groovy");
  }

  public void testEnumConstant() throws Exception {
    PsiReference ref = configureByFile("enumConstant/A.groovy");
    TestCase.assertTrue(ref.resolve() instanceof GrEnumConstant);
  }

  public void testStackOverflow() throws Exception {
    doTest("stackOverflow/A.groovy");
  }

  public void testFromDifferentCaseClause() {
    PsiReference ref = configureByFile("fromDifferentCaseClause/A.groovy");
    TestCase.assertNull(ref.resolve());
  }

  public void testNotSettingProperty() {
    PsiReference ref = configureByFile("notSettingProperty/A.groovy");
    TestCase.assertNull(ref.resolve());
  }

  public void testGrvy633() {
    PsiReference ref = configureByFile("grvy633/A.groovy");
    TestCase.assertNull(ref.resolve());
  }

  public void testGrvy575() throws Exception {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    doTest("grvy575/A.groovy");
  }

  public void testGrvy747() {
    PsiReference ref = configureByFile("grvy747/A.groovy");
    TestCase.assertTrue(ref.resolve() instanceof GrField);
  }

  public void testClosureCall() {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    PsiReference ref = configureByFile("closureCall/ClosureCall.groovy");
    TestCase.assertTrue(ref.resolve() instanceof GrVariable);
  }

  public void testUnderscoredField() {
    PsiReference ref = configureByFile("underscoredField/UnderscoredField.groovy");
    final GrField field = UsefulTestCase.assertInstanceOf(ref.resolve(), GrField.class);
    TestCase.assertFalse(ref.isReferenceTo(field.getGetters()[0]));
    TestCase.assertTrue(ref.isReferenceTo(field));
  }

  public void testPropertyWithoutField1() {
    PsiReference ref = configureByFile("propertyWithoutField1/PropertyWithoutField1.groovy");
    UsefulTestCase.assertInstanceOf(ref.resolve(), GrMethod.class);
  }

  public void testPropertyWithoutField2() {
    PsiReference ref = configureByFile("propertyWithoutField2/PropertyWithoutField2.groovy");
    UsefulTestCase.assertInstanceOf(ref.resolve(), GrMethod.class);
  }

  public void testFieldAssignedInTheSameMethod() {
    PsiReference ref = configureByFile("fieldAssignedInTheSameMethod/FieldAssignedInTheSameMethod.groovy");
    UsefulTestCase.assertInstanceOf(ref.resolve(), GrField.class);
  }

  public void testPrivateFieldAssignment() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
      
                                  class Aaaaa {
                                    final def aaa
       \s
                                    def foo() {
                                      a<caret>aa = 2
                                    }
                                  }\
      """);
    PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    UsefulTestCase.assertInstanceOf(reference.resolve(), GrField.class);
  }

  public void testOverriddenGetter() {
    myFixture.configureByText("a.groovy", """
      interface Foo {
                                                      def getFoo()
                                                    }
                                                    interface Bar extends Foo {
                                                      def getFoo()
                                                    }
       \s
                                                    Bar b
                                                    b.fo<caret>o""");
    PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    TestCase.assertEquals("Bar", UsefulTestCase.assertInstanceOf(reference.resolve(), GrMethod.class).getContainingClass().getName());
  }

  public void testIDEADEV40403() {
    myFixture.configureByFile("IDEADEV40403/A.groovy");
    PsiReference reference = findReference();
    PsiElement resolved = reference.resolve();
    PsiClass clazz = UsefulTestCase.assertInstanceOf(resolved, PsiMethod.class).getContainingClass();
    TestCase.assertEquals("Script", clazz.getName());
  }

  public void testBooleanGetterPropertyAccess() {
    myFixture.configureByText("a.groovy", "print([].em<caret>pty)");
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, PsiMethod.class);
  }

  public PsiReference findReference() { return myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset()); }

  public void testTriplePropertyUsages() {
    myFixture.configureByText("a.groovy", """
      
        class Foo {
          def bar
          def zoo = <caret>bar
        }
       \s\
      """);
    PsiReference ref = findReference();
    GrField target = UsefulTestCase.assertInstanceOf(ref.resolve(), GrField.class);
    TestCase.assertTrue(ref.isReferenceTo(target));
    TestCase.assertFalse(ref.isReferenceTo(target.getGetters()[0]));
    TestCase.assertFalse(ref.isReferenceTo(target.getSetter()));
  }

  public void testAliasedStaticImport() {
    myFixture.addClass("""
                         class Main {
                         static def foo=4
                         """);

    myFixture.configureByText("a.groovy", """
      import static Main.foo as bar
      print ba<caret>r
      }
      """);
    PsiReference ref = findReference();
    PsiField target = UsefulTestCase.assertInstanceOf(ref.resolve(), PsiField.class);
    TestCase.assertEquals("foo", target.getName());
  }

  private void doTest(String fileName) {
    PsiReference ref = configureByFile(fileName);
    PsiElement resolved = ref.resolve();
    TestCase.assertTrue(resolved instanceof GrVariable);
  }

  private void doUndefinedVarTest(String fileName) {
    PsiReference ref = configureByFile(fileName);
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrBindingVariable.class);
  }

  public void testBooleanProperty() {
    myFixture.configureByText("Abc.groovy", """
      class A{
         boolean getFoo(){return true}
         boolean isFoo(){return false}
      }
      print new A().f<caret>oo
      """);
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    TestCase.assertNotNull(resolved);
    assertEquals("getFoo", ((PsiMethod)resolved).getName());
  }

  public void testExplicitBooleanProperty() {
    myFixture.configureByText("Abc.groovy", """
      class A{
         boolean foo
      }
      print new A().f<caret>oo
      """);
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    assertEquals("getFoo", ((PsiMethod)resolved).getName());
  }

  public void testStaticFieldAndNonStaticGetter() {
    myFixture.configureByText("Abc.groovy", "print Float.N<caret>aN");
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, PsiField.class);
  }

  public void testPropertyAndFieldDeclarationInsideClass() {
    myFixture.configureByText("a.groovy", """
       class Foo {
        def foo
        public def foo
        def bar() {
          print fo<caret>o
        }
      }
      """);
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrField.class);
    TestCase.assertTrue(((GrField)resolved).getModifierList().hasExplicitVisibilityModifiers());
  }

  public void testPropertyAndFieldDeclarationOutsideClass() {
    myFixture.configureByText("a.groovy", """
      class Foo {
        def foo
        public def foo
      
        def bar() {
          print foo
        }
      }
      print new Foo().fo<caret>o
      """);
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrAccessorMethod.class);
  }

  public void testPropertyAndFieldDeclarationWithSuperClass1() {
    myFixture.configureByText("a.groovy", """
      class Bar{
        def foo
      }
      class Foo extends Bar {
        public def foo
      
        def bar() {
          print foo
        }
      }
      print new Foo().fo<caret>o
      """);
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrAccessorMethod.class);
  }

  public void testPropertyAndFieldDeclarationWithSuperClass2() {
    myFixture.configureByText("a.groovy", """
      class Bar{
        def foo
      }
      class Foo extends Bar {
        public def foo
      
        def bar() {
          print f<caret>oo
        }
      }
      print new Foo().foo
      """);
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrField.class);
    TestCase.assertTrue(((GrField)resolved).getModifierList().hasExplicitVisibilityModifiers());
  }

  public void testPropertyAndFieldDeclarationWithSuperClass3() {
    myFixture.configureByText("a.groovy", """
      class Bar{
        public def foo
      }
      class Foo extends Bar {
        def foo
      
        def bar() {
          print foo
        }
      }
      print new Foo().fo<caret>o
      """);
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrAccessorMethod.class);
  }

  public void testPropertyAndFieldDeclarationWithSuperClass4() {
    myFixture.configureByText("a.groovy", """
      class Bar{
        public def foo
      }
      class Foo extends Bar {
        def foo
      
        def bar() {
          print f<caret>oo
        }
      }
      print new Foo().foo
      """);
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrField.class);
    TestCase.assertFalse(((GrField)resolved).getModifierList().hasExplicitVisibilityModifiers());
  }

  public void testReadAccessToStaticallyImportedProperty() {

    myFixture.addFileToProject("a.groovy", """
      class Foo {
        static def bar
      }
      """);
    myFixture.configureByText("b.groovy", """
      import static Foo.bar
      print ba<caret>r
      """);
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrAccessorMethod.class);
  }

  public void testWriteAccessToStaticallyImportedProperty() {
    myFixture.addFileToProject("a.groovy", """
      class Foo {
        static def bar
      }
      """);
    myFixture.configureByText("b.groovy", """
      import static Foo.bar
      ba<caret>r = 2
      """);
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrAccessorMethod.class);
  }

  public void testGetterToStaticallyImportedProperty() {
    myFixture.addFileToProject("a.groovy", """
      class Foo {
        static def bar
      }
      """);
    myFixture.configureByText("b.groovy", """
      import static Foo.bar
      set<caret>Bar(2)
      """);
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrAccessorMethod.class);
  }

  public void testPropertyInCallExpression() {
    myFixture.configureByText("a.groovy", """
      class Foo {
        def foo = {
          return {int i -> print i}
        }
      
        def foo(String s){
          print s
        }
      }
      new Foo().fo<caret>o(2)\
      """);
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();

    UsefulTestCase.assertInstanceOf(resolved, GrAccessorMethod.class);
  }

  public void test_property_vs_field_in_call_from_outside() {
    GrMethod method = resolveByText("""
                                      class C {
                                        def foo = { 42 }
                                        def getFoo() { return { 43 } }
                                      }
                                      new C().<caret>foo(2)
                                      """, GrMethod.class);
    assertFalse(method instanceof GrAccessorMethod);
  }

  public void testPropertyImportedOnDemand() {
    myFixture.addFileToProject("foo/A.groovy", "package foo; class Foo {static def foo}");
    myFixture.configureByText("B.groovy", """
      package foo
      import static Foo.*
      print fo<caret>o
      """);

    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrAccessorMethod.class);
  }

  public void testSetterToAliasedImportedProperty() {
    myFixture.addFileToProject("a.groovy", """
      class Foo {
        static def bar
      }
      """);
    myFixture.configureByText("b.groovy", """
      import static Foo.bar as foo
      set<caret>Foo(2)
      """);
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrAccessorMethod.class);
  }

  public void testPhysicalSetterToStaticallyImportedProperty() {
    myFixture.addFileToProject("a.groovy", """
      class Foo {
        static def bar
        static def setBar(def bar){}
      }
      """);
    myFixture.configureByText("b.groovy", """
      import static Foo.bar as foo
      set<caret>Foo(2)
      """);
    PsiReference ref = findReference();
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrMethod.class);
  }

  public void testPropertyUseInCategory() {
    PsiReference ref = configureByFile("propertyUseInCategory/a.groovy");
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testCollectionItemFields() {
    PsiReference ref = configureByFile("collectionItemFields/A.groovy");
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, PsiField.class);
  }

  public void testFieldAccessInStaticContext() {
    PsiReference ref = configureByFile("fieldAccessInStaticContext/A.groovy");
    GroovyResolveResult resolveResult = ((GrReferenceExpression)ref).advancedResolve();
    TestCase.assertFalse(resolveResult.isStaticsOK());
  }

  public void testFieldAccessInClosureVsStaticContext() {
    PsiReference ref = configureByFile("fieldAccessInClosureVsStaticContext/A.groovy");
    GroovyResolveResult resolveResult = ((GrReferenceExpression)ref).advancedResolve();
    TestCase.assertTrue(resolveResult.isStaticsOK());
  }

  public void testUpperCaseFieldAndGetter() {
    TestCase.assertTrue(resolve("A.groovy") instanceof GrField);
  }

  public void testUpperCaseFieldWithoutGetter() {
    UsefulTestCase.assertInstanceOf(resolve("A.groovy"), GrField.class);
  }

  public void testGetterWithUpperCaseFieldReference() {
    TestCase.assertNull(resolve("A.groovy"));
  }

  public void testCommandExpressions() {
    UsefulTestCase.assertInstanceOf(resolve("A.groovy"), GrAccessorMethod.class);
  }

  public void testStaticFieldOfInterface() {
    final GroovyResolveResult result = advancedResolve("A.groovy");
    TestCase.assertTrue(result.isStaticsOK());
  }

  public void testNonStaticFieldInStaticContext() {
    final GroovyResolveResult result = advancedResolve("A.groovy");
    TestCase.assertFalse(result.isStaticsOK());
  }

  public void testPropertyInExprStatement() {
    PsiElement result = resolve("A.groovy");
    UsefulTestCase.assertInstanceOf(result, GrAccessorMethod.class);
  }

  public void testPreferAlias() {
    myFixture.addFileToProject("a/B.groovy", "package a; class B {public static def f1; public static def f2}");
    TestCase.assertEquals("f2", ((GrField)resolve("A.groovy")).getName());
  }

  public void testF1property() {
    UsefulTestCase.assertInstanceOf(resolve("A.groovy"), GrAccessorMethod.class);
  }

  public void testAnonymousClassFieldAndLocalVar() {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    final PsiElement resolved = resolve("A.groovy");
    UsefulTestCase.assertInstanceOf(resolved, PsiVariable.class);
    TestCase.assertTrue(PsiUtil.isLocalVariable(resolved));
  }

  public void _testResolveForVarOutsideOfFor() {
    final PsiElement resolved = resolve("A.groovy");
    UsefulTestCase.assertInstanceOf(resolved, GrParameter.class);
  }

  public void testDontResolveForVarOutsideOfFor() {
    TestCase.assertNull(resolve("A.groovy"));
  }

  public void testOperatorOverloading() {
    UsefulTestCase.assertInstanceOf(resolve("A.groovy"), GrAccessorMethod.class);
  }

  public void testResolveClosureOverloader() {
    UsefulTestCase.assertInstanceOf(resolve("A.groovy"), GrAccessorMethod.class);
  }

  public void testJavaLoggingTransform() {
    myFixture.addClass("package groovy.util.logging; public @interface Log { String value() default \"\"; }");
    PsiReference ref = configureByText("@groovy.util.logging.Log class Foo { { lo<caret>g.inf } }");
    assertEquals("java.util.logging.Logger",
                 UsefulTestCase.assertInstanceOf(ref.resolve(), PsiVariable.class).getType().getCanonicalText());
  }

  public void testNonLoggingField() {
    myFixture.addClass("package groovy.util.logging; public @interface Log { String value() default \"\"; }");
    assertNull(configureByText("@groovy.util.logging.Log class Foo { { alo<caret>g.inf } }").resolve());
  }

  public void testJavaLoggingTransformCustomName() {
    myFixture.addClass("package groovy.util.logging; public @interface Log { String value() default \"\"; }");
    PsiReference ref = configureByText("@groovy.util.logging.Log('myLog') class Foo { { myLo<caret>g.inf } }");
    assertEquals("java.util.logging.Logger",
                 UsefulTestCase.assertInstanceOf(ref.resolve(), PsiVariable.class).getType().getCanonicalText());
  }

  public void testCommonsLoggingTransform() {
    myFixture.addClass("package groovy.util.logging; public @interface Commons { String value() default \"\"; }");
    PsiReference ref = configureByText("@groovy.util.logging.Commons('myLog') class Foo { { myLo<caret>g.inf } }");
    assertEquals("org.apache.commons.logging.Log",
                 UsefulTestCase.assertInstanceOf(ref.resolve(), PsiVariable.class).getType().getCanonicalText());
  }

  public void testFieldTransform() {
    addGroovyTransformField();
    PsiReference ref = configureByText("""
                                         @groovy.transform.Field def aaa = 2
                                           def foo() { println <caret>aaa }
                                          \s""");
    assertTrue(ref.resolve() instanceof GrVariable);
  }

  public void testScriptFieldVariableOutside() {
    myFixture.addFileToProject("Foo.groovy", "if (true) { @groovy.transform.Field def a = 2 }");

    PsiReference ref = configureByText("println new Foo().<caret>a");
    assertTrue(ref.resolve() instanceof GrVariable);
  }

  public void testScriptVariableFromScriptMethod() {
    PsiReference ref = configureByText("""
                                         def aaa = 2
                                         def foo() { println <caret>aaa }
                                         """);
    assertNull(ref.resolve());
  }

  public void testConfigObject() {
    PsiReference ref = configureByText("""
                                         def config = new ConfigObject()
                                         print config.config<caret>File
                                         """);
    assertNotNull(ref.resolve());
  }

  public void testResolveInsideWith0() {
    PsiElement resolved = resolve("a.groovy", GrAccessorMethod.class);
    assertEquals("A", ((PsiMember)resolved).getContainingClass().getName());
  }

  public void testResolveInsideWith1() {
    PsiElement resolved = resolve("a.groovy", GrAccessorMethod.class);
    assertEquals("B", ((PsiMember)resolved).getContainingClass().getName());
  }

  public void testLocalVarVsFieldInWithClosure() {
    // TODO disableTransformations getTestRootDisposable()
    PsiReference ref = configureByText("""
                                         class Test {
                                           def var
                                         }
                                         int var = 4
                                         new Test().with() {
                                           print v<caret>ar
                                         }
                                         """);
    TestCase.assertFalse(ref.resolve() instanceof GrField);
    TestCase.assertTrue(ref.resolve() instanceof GrVariable);
  }

  public void testCapitalizedProperty1() {
    PsiReference ref = configureByText("""
                                         class A {
                                           def Prop
                                         }
                                         
                                         new A().Pro<caret>p
                                         """);
    TestCase.assertNotNull(ref.resolve());
  }

  public void testCapitalizedProperty2() {
    PsiReference ref = configureByText("""
                                         class A {
                                           def Prop
                                         }
                                         
                                         new A().pro<caret>p
                                         """);
    TestCase.assertNotNull(ref.resolve());
  }

  public void testCapitalizedProperty3() {
    PsiReference ref = configureByText("""
                                         class A {
                                           def prop
                                         }
                                         
                                         new A().Pro<caret>p
                                         """);
    TestCase.assertNull(ref.resolve());
  }

  public void testCapitalizedProperty4() {
    PsiReference ref = configureByText("""
                                         class A {
                                           def prop
                                         }
                                         
                                         new A().pro<caret>p
                                         """);
    TestCase.assertNotNull(ref.resolve());
  }

  public void testGetChars() {
    PsiReference ref = configureByText("""
                                         'abc'.cha<caret>rs
                                         """);
    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrGdkMethod.class);
    PsiMethod method = ((GrGdkMethod)resolved).getStaticMethod();

    TestCase.assertEquals("java.lang.CharSequence", method.getParameterList().getParameters()[0].getType().getCanonicalText());
  }

  public void testLocalVarNotAvailableInClass() {
    PsiReference ref = configureByText("""
                                         def aa = 5
                                         
                                         class Inner {
                                           def foo() {
                                             print a<caret>a
                                           }
                                         }
                                         """);
    TestCase.assertNull(ref.resolve());
  }

  public void testLocalVarNotAvailableInMethod() {
    PsiReference ref = configureByText("""
                                         def aa = 5
                                         
                                         def foo() {
                                           print a<caret>a
                                         }
                                         """);
    TestCase.assertNull(ref.resolve());
  }

  public void testScriptFieldNotAvailableInClass() {
    addGroovyTransformField();
    PsiReference ref = configureByText("""
                                         import groovy.transform.Field
                                         
                                         @Field
                                         def aa = 5
                                         
                                         class X {
                                           def foo() {
                                             print a<caret>a
                                           }
                                         }
                                         """);
    TestCase.assertNull(ref.resolve());
  }

  public void testInitializerOfScriptField() {
    addGroovyTransformField();
    PsiReference ref = configureByText("""
                                         import groovy.transform.Field
                                         
                                         def xx = 5
                                         @Field
                                         def aa = 5 + x<caret>x
                                         """);
    TestCase.assertNull(ref.resolve());
  }

  public void testInitializerOfScriptField2() {
    addGroovyTransformField();
    PsiReference ref = configureByText("""
                                         import groovy.transform.Field
                                         
                                         @Field
                                         def xx = 5
                                         
                                         @Field
                                         def aa = 5 + x<caret>x
                                         """);
    UsefulTestCase.assertInstanceOf(ref.resolve(), GrVariable.class);
  }

  public void testAliasedImportedPropertyWithGetterInAlias() {
    myFixture.addFileToProject("Foo.groovy", """
      class Foo {
        static def prop = 2
      }
      """);

    PsiReference ref = configureByText("""
                                         import static Foo.getProp as getOther
                                         
                                         print othe<caret>r
                                         """);

    UsefulTestCase.assertInstanceOf(ref.resolve(), GrAccessorMethod.class);
  }

  public void testPrefLocalVarToScriptName() {
    myFixture.addFileToProject("foo.groovy", "print 2");

    PsiReference ref = configureByText("""
                                         class Bar {
                                             public float FOO = 1.23f
                                             public float ZZZ = 5.78f
                                             public float pi = 3.14f
                                             public float xxx = 6f
                                         
                                             void doSmth() {}
                                         }
                                         
                                         class User {
                                             public static void main(String[] args) {
                                                 Bar foo = new Bar()
                                                 foo.bar()
                                                 println foo.F<caret>OO * foo.ZZZ * foo.pi * foo.xxx
                                                 foo.doSmth()
                                             }
                                         }
                                         """);

    UsefulTestCase.assertInstanceOf(ref.resolve(), GrField.class);
  }

  public void testResolveEnumConstantInsideItsInitializer() {
    PsiReference ref = configureByText("""
                                         enum MyEnum {
                                             CONST {
                                                 void get() {
                                                     C<caret>ONST
                                                 }
                                             }
                                         }
                                         """);
    TestCase.assertNotNull(ref);
  }

  public void testSpreadWithIterator() {
    final PsiReference ref = configureByText("""
                                               class Person { String name }
                                               class Twin {
                                                   Person one, two
                                                   def iterator() {
                                                       return [one, two].iterator()
                                                   }
                                               }
                                               
                                               def tw = new Twin(one: new Person(name:'Tom'),
                                                                 two: new Person(name:'Tim'))
                                               assert tw*.nam<caret>e == ['Tom', 'Tim']
                                               """);

    final PsiMember resolved = UsefulTestCase.assertInstanceOf(ref.resolve(), PsiMember.class);
    TestCase.assertNotNull(resolved.getContainingClass());
    TestCase.assertEquals("Person", resolved.getContainingClass().getName());
  }

  public void testAutoSpread() {
    PsiReference ref = configureByText("""
                                         class A {
                                             String getString() {return "a";}
                                         }
                                         
                                         println ([[new A()]].stri<caret>ng)
                                         """);

    final PsiElement resolved = ref.resolve();

    TestCase.assertNotNull(resolved);
  }

  public void testImportedProperty() {
    myFixture.addFileToProject("pack/Const.groovy", """
      package pack
      
      class Const {
        static final int Field1 = 2
      }
      """);
    resolveByText("""
                    import static pack.Const.getField1
                    
                    print Fie<caret>ld1
                    """, null);
  }

  public void testImportedProperty2() {
    myFixture.addFileToProject("pack/Const.groovy", """
      package pack
      
      class Const {
        static final int Field1 = 2
      }
      """);

    resolveByText("""
                    import static pack.Const.getField1
                    
                    print fie<caret>ld1
                    """, GrAccessorMethod.class);
  }

  public void testImportedProperty3() {
    myFixture.addFileToProject("pack/Const.groovy", """
      package pack
      
      class Const {
        static final int Field1 = 2
      }
      """);

    resolveByText("""
                    import static pack.Const.getField1 as getBar
                    
                    print Ba<caret>r
                    """, null);
  }

  public void testImportedProperty4() {
    myFixture.addFileToProject("pack/Const.groovy", """
      package pack
      
      class Const {
        static final int Field1 = 2
      }
      """);

    resolveByText("""
                    import static pack.Const.getField1 as getBar
                    
                    print ba<caret>r
                    """, GrAccessorMethod.class);
  }

  public void testImportedProperty5() {
    myFixture.addFileToProject("pack/Const.groovy", """
      package pack
      
      class Const {
        static final int field1 = 2
      }
      """);

    resolveByText("""
                    import static pack.Const.getField1
                    
                    print Fie<caret>ld1
                    """, null);
  }

  public void testLocalVarVsClassFieldInAnonymous() {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    GrVariable resolved = resolveByText("""
                                          class A {
                                            public foo
                                          }
                                          
                                          def foo = 4
                                          
                                          new A() {
                                            def foo() {
                                              print fo<caret>o
                                            }
                                          }
                                          """, GrVariable.class);
    assertFalse(resolved instanceof PsiField);
  }

  public void testInterfaceDoesNotResolveWithExpressionQualifier() {
    PsiReference ref = configureByText("""
                                         class Foo {
                                           interface Inner {
                                           }
                                         
                                           public Inner = 5
                                         }
                                         
                                         new Foo().Inn<caret>er
                                         """);

    UsefulTestCase.assertInstanceOf(ref.resolve(), PsiField.class);
  }

  public void testInterfaceDoesNotResolveWithExpressionQualifier2() {
    PsiReference ref = configureByText("""
                                         class Foo {
                                           interface Inner {
                                           }
                                         
                                           public Inner = 5
                                         }
                                         
                                         def foo = new Foo()
                                         print foo.Inn<caret>er
                                         """);

    UsefulTestCase.assertInstanceOf(ref.resolve(), PsiField.class);
  }

  public void testResolveBinding1() {
    resolveByText("""
                    abc = 4
                    
                    print ab<caret>c
                    """, GrBindingVariable.class);
  }

  public void ignoreTestResolveBinding2() {
    resolveByText("""
                    print ab<caret>c
                    
                    abc = 4
                    """, GrBindingVariable.class);
  }

  public void testResolveBinding3() {
    resolveByText("""
                    a<caret>bc = 4
                    
                    print abc
                    """, GrBindingVariable.class);
  }

  public void testResolveBinding4() {
    resolveByText("""
                    print abc
                    
                    a<caret>bc = 4
                    """, GrBindingVariable.class);
  }

  public void testResolveBinding5() {
    resolveByText("""
                    def foo() {
                      abc = 4
                    }
                    
                    def bar() {
                      print ab<caret>c
                    }
                    """, null);
  }

  public void testResolveBinding6() {
    resolveByText("""
                    def foo() {
                      print ab<caret>c
                    }
                    
                    def bar() {
                      abc = 4
                    }
                    """, null);
  }

  public void testResolveBinding7() {
    resolveByText("""
                    def foo() {
                      a<caret>bc = 4
                    }
                    
                    def bar() {
                      print abc
                    }
                    """, null);
  }

  public void testResolveBinding8() {
    resolveByText("""
                    def foo() {
                      print abc
                    }
                    
                    def bar() {
                      a<caret>bc = 4
                    }
                    """, null);
  }

  public void testBinding9() {
    resolveByText("""
                    a<caret>a = 5
                    print aa
                    aa = 6
                    print aa
                    """, GrBindingVariable.class);
  }

  public void testBinding10() {
    resolveByText("""
                    aa = 5
                    print a<caret>a
                    aa = 6
                    print aa
                    """, GrBindingVariable.class);
  }

  public void testBinding11() {
    resolveByText("""
                    aa = 5
                    print aa
                    a<caret>a = 6
                    print aa
                    """, GrBindingVariable.class);
  }

  public void testBinding12() {
    resolveByText("""
                    aa = 5
                    print aa
                    aa = 6
                    print a<caret>a
                    """, GrBindingVariable.class);
  }

  public void testBinding13() {
    resolveByText("""
                    aaa = 1
                    
                    def foo() {
                      aa<caret>a
                    }
                    """, GrBindingVariable.class);
  }

  public void testBinding14() {
    resolveByText("""
                    def foo() {
                      aa<caret>a
                    }
                    
                    aaa = 1
                    """, null);
  }

  public void testVarVsPackage1() {
    myFixture.addClass("package p; public class A {}");
    resolveByText("""
                    def p = [A:5]
                    
                    print <caret>p.A
                    """, PsiPackage.class);
  }

  public void testVarVsPackage2() {
    myFixture.addClass("package p; public class A {}");
    ThrowingTransformation.disableTransformations(getTestRootDisposable());

    resolveByText("""
                    def p = [A:5]
                    
                    print <caret>p
                    """, GrVariable.class);
  }

  public void testVarVsPackage3() {
    myFixture.addClass("package p; public class A {}");
    ThrowingTransformation.disableTransformations(getTestRootDisposable());

    resolveByText("""
                    def p = [A:{2}]
                    
                    print <caret>p.A()
                    """, GrVariable.class);
  }

  public void testVarVsPackage4() {
    myFixture.addClass("package p; public class A {public static int foo(){return 2;}}");

    resolveByText("""
                    def p = [A:[foo:{-2}]]
                    
                    print <caret>p.A.foo()
                    """, PsiPackage.class);
  }

  public void testVarVsClass1() {
    myFixture.addClass("package p; public class A {public static int foo() {return 1;}}");
    ThrowingTransformation.disableTransformations(getTestRootDisposable());

    resolveByText("""
                    import p.A
                    def A = [a:{-1}]
                    print <caret>A
                    """, GrVariable.class);
  }

  public void testVarVsClass2() {
    myFixture.addClass("package p; public class A {public static int foo() {return 1;}}");
    ThrowingTransformation.disableTransformations(getTestRootDisposable());

    resolveByText("""
                    import p.A
                    def A = [a:{-1}]
                    print <caret>A.a()
                    """, GrVariable.class);
  }

  public void testTraitPublicField1() {
    resolveByText("""
                    trait T {
                      public int field = 4
                    }
                    
                    class C implements T {
                    
                      void foo() {
                        print T__fie<caret>ld
                      }
                    }
                    """, GrField.class);
  }

  public void testTraitPublicField2() {
    resolveByText("""
                    trait T {
                      public int field = 4
                    
                      void foo() {
                        print fiel<caret>d
                      }
                    }
                    """, GrField.class);
  }

  public void testTraitPublicField3() {
    resolveByText("""
                    trait T {
                      public int field = 4
                    
                      void foo() {
                        print T__fie<caret>ld
                      }
                    }
                    """, GrField.class);
  }

  public void testTraitPublicField4() {
    resolveByText("""
                    trait T {
                      public int field = 4
                    }
                    
                    class C implements T {}
                    
                    new C().T__fiel<caret>d
                    """, GrField.class);
  }

  public void testTraitProperty1() {
    resolveByText("""
                    trait T {
                      int prop = 4
                    }
                    
                    class C extends T {}
                    
                    new C().pr<caret>op
                    """, GrTraitMethod.class);
  }

  public void testTraitProperty2() {
    resolveByText("""
                    trait T {
                      int prop = 4
                    }
                    
                    class C extends T {
                      def bar() {
                        print pro<caret>p
                      }
                    }
                    """, GrTraitMethod.class);
  }

  public void testTraitProperty3() {
    resolveByText("""
                    trait T {
                      int prop = 4
                    
                      void foo() {
                        print pro<caret>p
                      }
                    }
                    """, GrField.class);
  }

  public void testTraitPropertyFromAsOperator1() {
    resolveByText("""
                    trait A {
                      def foo = 5
                    }
                    class B {
                      def bar() {}
                    }
                    
                    def v = new B() as A
                    print v.fo<caret>o
                    """, GrAccessorMethod.class);
  }

  public void testTraitPropertyFromAsOperator2() {
    resolveByText("""
                    trait A {
                      public foo = 5
                    }
                    class B {
                      def bar() {}
                    }
                    
                    def v = new B() as A
                    print v.A<caret>__foo
                    """, GrField.class);
  }

  public void testTraitField1() {
    resolveByText("""
                          trait T {
                            public foo = 4
                          }
                    
                          class X implements T{
                            def bar() {
                              print fo<caret>o
                            }
                          }
                    """, null);
  }

  public void testTraitField2() {
    resolveByText("""
                          trait T {
                            public foo
                    
                            def bar() {
                              print fo<caret>o
                            }
                          }
                    """, PsiField.class);
  }

  public void testSetterOverloading() {
    PsiMethod method = resolveByText("""
                                       class A {
                                           void setFoo(File f) {}
                                       
                                           void setFoo(String s) {}
                                       }
                                       
                                       new A().fo<caret>o = ""
                                       """, GrMethod.class);
    assertEquals("java.lang.String", method.getParameterList().getParameters()[0].getType().getCanonicalText());
  }

  public void testOnDemandStaticImport() {
    myFixture.addClass("""
                         package somepackage;
                         public class Holder {
                           public static Object getStuff() { return new Object(); }
                         }
                         """);

    PsiMethod method = resolveByText("""
                                       import static somepackage.Holder.*
                                       class Foo {
                                         static field = stu<caret>ff
                                       }
                                       """, PsiMethod.class);
    assertEquals("getStuff", method.getName());
  }

  public void testPreferLocalOverMapKey() {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());

    resolveByText("def abc = 42; [:].with { <caret>abc }", GrVariable.class);
  }

  public void testClassPropertyVsInstanceProperty() {
    PsiMethod property = resolveByText("""
                                         class A {
                                           int getName() { 42 }
                                         }
                                         
                                         println A.<caret>name
                                         """, PsiMethod.class);
    assertEquals("java.lang.Class", property.getContainingClass().getQualifiedName());
  }

  public void testDontResolveToFieldInMethodCall() {
    resolveByText("""
                    class Fff {
                      List<String> testThis = [""]
                      def usage() { <caret>testThis(1) }
                      def testThis(a) {}
                    }
                    """, GrMethod.class);
  }

  public void testStaticFieldViaClassInstance() {
    resolveByText("""
                    class A { public static someStaticField = 42 }
                    def a = A // class instance
                    a.<caret>someStaticField
                    """, GrField.class);
  }

  public void testGPathInArray() {
    resolveByText("""
                    class A {
                        int foo
                        A(int x) { foo = x }
                    }
                    
                    A[] ai = [new A(1), new A(2)].toArray()
                    
                    println(ai.fo<caret>o)
                    """, GrAccessorMethod.class);
  }

  public void testGPathInArray2() {
    resolveByText("""
                    class A {
                        int length
                        A(int x) { length = x }
                    }
                    
                    A[] ai = [new A(1), new A(2)].toArray()
                    
                    println(ai.len<caret>gth)
                    """, GrField.class);
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "resolve/property/";
  }
}
