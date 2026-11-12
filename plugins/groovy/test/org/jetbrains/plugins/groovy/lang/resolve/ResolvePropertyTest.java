// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
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
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolvePropertyTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GroovyResolveFixture> resolveFixture =
    groovyResolveFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "resolve/property/");

  @Test
  public void testParameter1() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    resolveFixture.get().resolve("A.groovy", GrParameter.class);
  }

  @Test
  public void testClosureParameter1() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    resolveFixture.get().resolve("A.groovy", GrParameter.class);
  }

  @Test
  public void testClosureOwner() {
    PsiReference ref = resolveFixture.get().configureByFile("closureOwner/A.groovy");
    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);
    });
  }

  @Test
  public void testLocal1() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    doTest("local1/A.groovy");
  }

  @Test
  public void testField1() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    doTest("field1/A.groovy");
  }

  @Test
  public void testField2() throws Exception {
    doTest("field2/A.groovy");
  }

  @Test
  public void testArrayLength() throws Exception {
    doTest("arrayLength/A.groovy");
  }

  @Test
  public void testFromGetter() {
    PsiReference ref = resolveFixture.get().configureByFile("fromGetter/A.groovy");
    ReadAction.runBlocking(() -> {
      assertTrue(ref.resolve() instanceof GrAccessorMethod);
    });
  }

  @Test
  public void testFromGetter2() {
    PsiReference ref = resolveFixture.get().configureByFile("fromGetter2/A.groovy");
    ReadAction.runBlocking(() -> {
      assertTrue(ref.resolve() instanceof GrAccessorMethod);
    });
  }

  @Test
  public void testFromSetter2() {
    PsiReference ref = resolveFixture.get().configureByFile("fromSetter2/A.groovy");
    ReadAction.runBlocking(() -> {
      assertTrue(ref.resolve() instanceof GrAccessorMethod);
    });
  }

  @Test
  public void testFromSetter() {
    PsiReference ref = resolveFixture.get().configureByFile("fromSetter/A.groovy");
    ReadAction.runBlocking(() -> {
      assertTrue(ref.resolve() instanceof GrAccessorMethod);
    });
  }

  @Test
  public void testCatchParameter() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    resolveFixture.get().resolve("CatchParameter.groovy", GrParameter.class);
  }

  @Test
  public void testCaseClause() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    doTest("caseClause/CaseClause.groovy");
  }

  @Test
  public void testGrvy104() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    doTest("grvy104/Test.groovy");
  }

  @Test
  public void testGrvy270() {
    PsiReference ref = resolveFixture.get().configureByFile("grvy270/Test.groovy");
    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }

  @Test
  public void testGrvy1483() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    resolveFixture.get().resolve("Test.groovy", GrVariable.class);
  }

  @Test
  public void testField3() {
    GrReferenceElement ref = (GrReferenceElement)resolveFixture.get().configureByFile("field3/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      GroovyResolveResult resolveResult = ref.advancedResolve();
      assertTrue(resolveResult.getElement() instanceof GrField);
      assertFalse(resolveResult.isValidResult());
    });
  }

  @Test
  public void testToGetter() {
    GrReferenceElement ref = (GrReferenceElement)resolveFixture.get().configureByFile("toGetter/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertTrue(resolved instanceof GrMethod);
      assertTrue(PropertyUtil.isSimplePropertyGetter((PsiMethod)resolved));
    });
  }

  @Test
  public void testToSetter() {
    GrReferenceElement ref = (GrReferenceElement)resolveFixture.get().configureByFile("toSetter/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertTrue(resolved instanceof GrMethod);
      assertTrue(PropertyUtil.isSimplePropertySetter((PsiMethod)resolved));
    });
  }

  @Test
  public void testUndefinedVar1() {
    PsiReference ref = resolveFixture.get().configureByFile("undefinedVar1/A.groovy");
    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrBindingVariable.class, resolved);
    });
  }

  @Test
  public void testRecursive1() {
    PsiReference ref = resolveFixture.get().configureByFile("recursive1/A.groovy");
    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertTrue(resolved instanceof GrField);
    });
  }

  @Test
  public void testRecursive2() {
    PsiReference ref = resolveFixture.get().configureByFile("recursive2/A.groovy");
    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertTrue(resolved instanceof GrMethod);
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT, ((GrMethod)resolved).getReturnType().getCanonicalText());
    });
  }

  @Test
  public void testUndefinedVar2() throws Exception {
    doUndefinedVarTest("undefinedVar2/A.groovy");
  }

  @Test
  public void testUndefinedVar3() {
    resolveFixture.get().resolveByText("""
                    
                      (aa, b) = [1, 4]
                      c = a<caret>a
                     \s\
                    """, GrBindingVariable.class);
  }

  @Test
  public void testDefinedVar1() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    resolveFixture.get().resolve("A.groovy", GrVariable.class);
  }

  @Test
  public void testOperatorOverload() throws Exception {
    doTest("operatorOverload/A.groovy");
  }

  @Test
  public void testEnumConstant() throws Exception {
    PsiReference ref = resolveFixture.get().configureByFile("enumConstant/A.groovy");
    ReadAction.runBlocking(() -> {
      assertTrue(ref.resolve() instanceof GrEnumConstant);
    });
  }

  @Test
  public void testStackOverflow() throws Exception {
    doTest("stackOverflow/A.groovy");
  }

  @Test
  public void testFromDifferentCaseClause() {
    PsiReference ref = resolveFixture.get().configureByFile("fromDifferentCaseClause/A.groovy");
    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }

  @Test
  public void testNotSettingProperty() {
    PsiReference ref = resolveFixture.get().configureByFile("notSettingProperty/A.groovy");
    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }

  @Test
  public void testGrvy633() {
    PsiReference ref = resolveFixture.get().configureByFile("grvy633/A.groovy");
    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }

  @Test
  public void testGrvy575() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    doTest("grvy575/A.groovy");
  }

  @Test
  public void testGrvy747() {
    PsiReference ref = resolveFixture.get().configureByFile("grvy747/A.groovy");
    ReadAction.runBlocking(() -> {
      assertTrue(ref.resolve() instanceof GrField);
    });
  }

  @Test
  public void testClosureCall() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    PsiReference ref = resolveFixture.get().configureByFile("closureCall/ClosureCall.groovy");
    ReadAction.runBlocking(() -> {
      assertTrue(ref.resolve() instanceof GrVariable);
    });
  }

  @Test
  public void testUnderscoredField() {
    PsiReference ref = resolveFixture.get().configureByFile("underscoredField/UnderscoredField.groovy");
    ReadAction.runBlocking(() -> {
      final GrField field = assertInstanceOf(GrField.class, ref.resolve());
      assertFalse(ref.isReferenceTo(field.getGetters()[0]));
      assertTrue(ref.isReferenceTo(field));
    });
  }

  @Test
  public void testPropertyWithoutField1() {
    PsiReference ref = resolveFixture.get().configureByFile("propertyWithoutField1/PropertyWithoutField1.groovy");
    ReadAction.runBlocking(() -> {
      assertInstanceOf(GrMethod.class, ref.resolve());
    });
  }

  @Test
  public void testPropertyWithoutField2() {
    PsiReference ref = resolveFixture.get().configureByFile("propertyWithoutField2/PropertyWithoutField2.groovy");
    ReadAction.runBlocking(() -> {
      assertInstanceOf(GrMethod.class, ref.resolve());
    });
  }

  @Test
  public void testFieldAssignedInTheSameMethod() {
    PsiReference ref = resolveFixture.get().configureByFile("fieldAssignedInTheSameMethod/FieldAssignedInTheSameMethod.groovy");
    ReadAction.runBlocking(() -> {
      assertInstanceOf(GrField.class, ref.resolve());
    });
  }

  @Test
  public void testPrivateFieldAssignment() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
      
                                  class Aaaaa {
                                    final def aaa
       \s
                                    def foo() {
                                      a<caret>aa = 2
                                    }
                                  }\
      """);
    ReadAction.runBlocking(() -> {
      PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertInstanceOf(GrField.class, reference.resolve());
    });
  }

  @Test
  public void testOverriddenGetter() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
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
    ReadAction.runBlocking(() -> {
      PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertEquals("Bar", assertInstanceOf(GrMethod.class, reference.resolve()).getContainingClass().getName());
    });
  }

  @Test
  public void testIDEADEV40403() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.configureByFile("IDEADEV40403/A.groovy");
    ReadAction.runBlocking(() -> {
      PsiReference reference = findReference();
      PsiElement resolved = reference.resolve();
      PsiClass clazz = assertInstanceOf(PsiMethod.class, resolved).getContainingClass();
      assertEquals("Script", clazz.getName());
    });
  }

  @Test
  public void testBooleanGetterPropertyAccess() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.configureByText("a.groovy", "print([].em<caret>pty)");
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);
    });
  }

  public PsiReference findReference() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture(); return myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset()); }

  @Test
  public void testTriplePropertyUsages() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.configureByText("a.groovy", """
      
        class Foo {
          def bar
          def zoo = <caret>bar
        }
       \s\
      """);
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      GrField target = assertInstanceOf(GrField.class, ref.resolve());
      assertTrue(ref.isReferenceTo(target));
      assertFalse(ref.isReferenceTo(target.getGetters()[0]));
      assertFalse(ref.isReferenceTo(target.getSetter()));
    });
  }

  @Test
  public void testAliasedStaticImport() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("""
                         class Main {
                         static def foo=4
                         """);

    myFixture.configureByText("a.groovy", """
      import static Main.foo as bar
      print ba<caret>r
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiField target = assertInstanceOf(PsiField.class, ref.resolve());
      assertEquals("foo", target.getName());
    });
  }

  private void doTest(String fileName) {
    PsiReference ref = resolveFixture.get().configureByFile(fileName);
    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertTrue(resolved instanceof GrVariable);
    });
  }

  private void doUndefinedVarTest(String fileName) {
    PsiReference ref = resolveFixture.get().configureByFile(fileName);
    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrBindingVariable.class, resolved);
    });
  }

  @Test
  public void testBooleanProperty() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.configureByText("Abc.groovy", """
      class A{
         boolean getFoo(){return true}
         boolean isFoo(){return false}
      }
      print new A().f<caret>oo
      """);
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertNotNull(resolved);
      assertEquals("getFoo", ((PsiMethod)resolved).getName());
    });
  }

  @Test
  public void testExplicitBooleanProperty() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.configureByText("Abc.groovy", """
      class A{
         boolean foo
      }
      print new A().f<caret>oo
      """);
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertEquals("getFoo", ((PsiMethod)resolved).getName());
    });
  }

  @Test
  public void testStaticFieldAndNonStaticGetter() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.configureByText("Abc.groovy", "print Float.N<caret>aN");
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiField.class, resolved);
    });
  }

  @Test
  public void testPropertyAndFieldDeclarationInsideClass() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.configureByText("a.groovy", """
       class Foo {
        def foo
        public def foo
        def bar() {
          print fo<caret>o
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrField.class, resolved);
      assertTrue(((GrField)resolved).getModifierList().hasExplicitVisibilityModifiers());
    });
  }

  @Test
  public void testPropertyAndFieldDeclarationOutsideClass() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
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
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrAccessorMethod.class, resolved);
    });
  }

  @Test
  public void testPropertyAndFieldDeclarationWithSuperClass1() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
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
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrAccessorMethod.class, resolved);
    });
  }

  @Test
  public void testPropertyAndFieldDeclarationWithSuperClass2() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
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
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrField.class, resolved);
      assertTrue(((GrField)resolved).getModifierList().hasExplicitVisibilityModifiers());
    });
  }

  @Test
  public void testPropertyAndFieldDeclarationWithSuperClass3() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
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
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrAccessorMethod.class, resolved);
    });
  }

  @Test
  public void testPropertyAndFieldDeclarationWithSuperClass4() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
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
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrField.class, resolved);
      assertFalse(((GrField)resolved).getModifierList().hasExplicitVisibilityModifiers());
    });
  }

  @Test
  public void testReadAccessToStaticallyImportedProperty() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();

    myFixture.addFileToProject("a.groovy", """
      class Foo {
        static def bar
      }
      """);
    myFixture.configureByText("b.groovy", """
      import static Foo.bar
      print ba<caret>r
      """);
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrAccessorMethod.class, resolved);
    });
  }

  @Test
  public void testWriteAccessToStaticallyImportedProperty() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("a.groovy", """
      class Foo {
        static def bar
      }
      """);
    myFixture.configureByText("b.groovy", """
      import static Foo.bar
      ba<caret>r = 2
      """);
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrAccessorMethod.class, resolved);
    });
  }

  @Test
  public void testGetterToStaticallyImportedProperty() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("a.groovy", """
      class Foo {
        static def bar
      }
      """);
    myFixture.configureByText("b.groovy", """
      import static Foo.bar
      set<caret>Bar(2)
      """);
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrAccessorMethod.class, resolved);
    });
  }

  @Test
  public void testPropertyInCallExpression() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
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
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();

      assertInstanceOf(GrAccessorMethod.class, resolved);
    });
  }

  @Test
  public void test_property_vs_field_in_call_from_outside() {
    GrMethod method = resolveFixture.get().resolveByText("""
                                      class C {
                                        def foo = { 42 }
                                        def getFoo() { return { 43 } }
                                      }
                                      new C().<caret>foo(2)
                                      """, GrMethod.class);
    ReadAction.runBlocking(() -> {
      assertFalse(method instanceof GrAccessorMethod);
    });
  }

  @Test
  public void testPropertyImportedOnDemand() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("foo/A.groovy", "package foo; class Foo {static def foo}");
    myFixture.configureByText("B.groovy", """
      package foo
      import static Foo.*
      print fo<caret>o
      """);

    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrAccessorMethod.class, resolved);
    });
  }

  @Test
  public void testSetterToAliasedImportedProperty() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("a.groovy", """
      class Foo {
        static def bar
      }
      """);
    myFixture.configureByText("b.groovy", """
      import static Foo.bar as foo
      set<caret>Foo(2)
      """);
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrAccessorMethod.class, resolved);
    });
  }

  @Test
  public void testPhysicalSetterToStaticallyImportedProperty() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
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
    ReadAction.runBlocking(() -> {
      PsiReference ref = findReference();
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrMethod.class, resolved);
    });
  }

  @Test
  public void testPropertyUseInCategory() {
    PsiReference ref = resolveFixture.get().configureByFile("propertyUseInCategory/a.groovy");
    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);
    });
  }

  @Test
  public void testCollectionItemFields() {
    PsiReference ref = resolveFixture.get().configureByFile("collectionItemFields/A.groovy");
    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiField.class, resolved);
    });
  }

  @Test
  public void testFieldAccessInStaticContext() {
    PsiReference ref = resolveFixture.get().configureByFile("fieldAccessInStaticContext/A.groovy");
    ReadAction.runBlocking(() -> {
      GroovyResolveResult resolveResult = ((GrReferenceExpression)ref).advancedResolve();
      assertFalse(resolveResult.isStaticsOK());
    });
  }

  @Test
  public void testFieldAccessInClosureVsStaticContext() {
    PsiReference ref = resolveFixture.get().configureByFile("fieldAccessInClosureVsStaticContext/A.groovy");
    ReadAction.runBlocking(() -> {
      GroovyResolveResult resolveResult = ((GrReferenceExpression)ref).advancedResolve();
      assertTrue(resolveResult.isStaticsOK());
    });
  }

  @Test
  public void testUpperCaseFieldAndGetter() {
    assertTrue(resolveFixture.get().resolve("A.groovy") instanceof GrField);
  }

  @Test
  public void testUpperCaseFieldWithoutGetter() {
    assertInstanceOf(GrField.class, resolveFixture.get().resolve("A.groovy"));
  }

  @Test
  public void testGetterWithUpperCaseFieldReference() {
    assertNull(resolveFixture.get().resolve("A.groovy"));
  }

  @Test
  public void testCommandExpressions() {
    assertInstanceOf(GrAccessorMethod.class, resolveFixture.get().resolve("A.groovy"));
  }

  @Test
  public void testStaticFieldOfInterface() {
    final GroovyResolveResult result = resolveFixture.get().advancedResolve("A.groovy");
    ReadAction.runBlocking(() -> {
      assertTrue(result.isStaticsOK());
    });
  }

  @Test
  public void testNonStaticFieldInStaticContext() {
    final GroovyResolveResult result = resolveFixture.get().advancedResolve("A.groovy");
    ReadAction.runBlocking(() -> {
      assertFalse(result.isStaticsOK());
    });
  }

  @Test
  public void testPropertyInExprStatement() {
    PsiElement result = resolveFixture.get().resolve("A.groovy");
    ReadAction.runBlocking(() -> {
      assertInstanceOf(GrAccessorMethod.class, result);
    });
  }

  @Test
  public void testPreferAlias() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("a/B.groovy", "package a; class B {public static def f1; public static def f2}");
    GrField field = (GrField)resolveFixture.get().resolve("A.groovy");
    assertEquals("f2", ReadAction.computeBlocking(() -> field.getName()));
  }

  @Test
  public void testF1property() {
    assertInstanceOf(GrAccessorMethod.class, resolveFixture.get().resolve("A.groovy"));
  }

  @Test
  public void testAnonymousClassFieldAndLocalVar() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    final PsiElement resolved = resolveFixture.get().resolve("A.groovy");
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiVariable.class, resolved);
      assertTrue(PsiUtil.isLocalVariable(resolved));
    });
  }

  public void _testResolveForVarOutsideOfFor() {
    final PsiElement resolved = resolveFixture.get().resolve("A.groovy");
    assertInstanceOf(GrParameter.class, resolved);
  }

  @Test
  public void testDontResolveForVarOutsideOfFor() {
    assertNull(resolveFixture.get().resolve("A.groovy"));
  }

  @Test
  public void testOperatorOverloading() {
    assertInstanceOf(GrAccessorMethod.class, resolveFixture.get().resolve("A.groovy"));
  }

  @Test
  public void testResolveClosureOverloader() {
    assertInstanceOf(GrAccessorMethod.class, resolveFixture.get().resolve("A.groovy"));
  }

  @Test
  public void testJavaLoggingTransform() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("package groovy.util.logging; public @interface Log { String value() default \"\"; }");
    PsiReference ref = resolveFixture.get().configureByText("@groovy.util.logging.Log class Foo { { lo<caret>g.inf } }");
    ReadAction.runBlocking(() -> {
      assertEquals("java.util.logging.Logger",
                   assertInstanceOf(PsiVariable.class, ref.resolve()).getType().getCanonicalText());
    });
  }

  @Test
  public void testNonLoggingField() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("package groovy.util.logging; public @interface Log { String value() default \"\"; }");
    PsiReference ref = resolveFixture.get().configureByText("@groovy.util.logging.Log class Foo { { alo<caret>g.inf } }");
    assertNull(ReadAction.computeBlocking(() -> ref.resolve()));
  }

  @Test
  public void testJavaLoggingTransformCustomName() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("package groovy.util.logging; public @interface Log { String value() default \"\"; }");
    PsiReference ref = resolveFixture.get().configureByText("@groovy.util.logging.Log('myLog') class Foo { { myLo<caret>g.inf } }");
    ReadAction.runBlocking(() -> {
      assertEquals("java.util.logging.Logger",
                   assertInstanceOf(PsiVariable.class, ref.resolve()).getType().getCanonicalText());
    });
  }

  @Test
  public void testCommonsLoggingTransform() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("package groovy.util.logging; public @interface Commons { String value() default \"\"; }");
    PsiReference ref = resolveFixture.get().configureByText("@groovy.util.logging.Commons('myLog') class Foo { { myLo<caret>g.inf } }");
    ReadAction.runBlocking(() -> {
      assertEquals("org.apache.commons.logging.Log",
                   assertInstanceOf(PsiVariable.class, ref.resolve()).getType().getCanonicalText());
    });
  }

  @Test
  public void testFieldTransform() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    LightGroovyFixtures.addGroovyTransformField(myFixture);
    PsiReference ref = resolveFixture.get().configureByText("""
                                         @groovy.transform.Field def aaa = 2
                                           def foo() { println <caret>aaa }
                                          \s""");
    ReadAction.runBlocking(() -> {
      assertTrue(ref.resolve() instanceof GrVariable);
    });
  }

  @Test
  public void testScriptFieldVariableOutside() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("Foo.groovy", "if (true) { @groovy.transform.Field def a = 2 }");

    PsiReference ref = resolveFixture.get().configureByText("println new Foo().<caret>a");
    ReadAction.runBlocking(() -> {
      assertTrue(ref.resolve() instanceof GrVariable);
    });
  }

  @Test
  public void testScriptVariableFromScriptMethod() {
    PsiReference ref = resolveFixture.get().configureByText("""
                                         def aaa = 2
                                         def foo() { println <caret>aaa }
                                         """);
    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }

  @Test
  public void testConfigObject() {
    PsiReference ref = resolveFixture.get().configureByText("""
                                         def config = new ConfigObject()
                                         print config.config<caret>File
                                         """);
    ReadAction.runBlocking(() -> {
      assertNotNull(ref.resolve());
    });
  }

  @Test
  public void testResolveInsideWith0() {
    PsiElement resolved = resolveFixture.get().resolve("a.groovy", GrAccessorMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("A", ((PsiMember)resolved).getContainingClass().getName());
    });
  }

  @Test
  public void testResolveInsideWith1() {
    PsiElement resolved = resolveFixture.get().resolve("a.groovy", GrAccessorMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("B", ((PsiMember)resolved).getContainingClass().getName());
    });
  }

  @Test
  public void testLocalVarVsFieldInWithClosure() {
    // TODO disableTransformations getTestRootDisposable()
    PsiReference ref = resolveFixture.get().configureByText("""
                                         class Test {
                                           def var
                                         }
                                         int var = 4
                                         new Test().with() {
                                           print v<caret>ar
                                         }
                                         """);
    ReadAction.runBlocking(() -> {
      assertFalse(ref.resolve() instanceof GrField);
      assertTrue(ref.resolve() instanceof GrVariable);
    });
  }

  @Test
  public void testCapitalizedProperty1() {
    PsiReference ref = resolveFixture.get().configureByText("""
                                         class A {
                                           def Prop
                                         }
                                         
                                         new A().Pro<caret>p
                                         """);
    ReadAction.runBlocking(() -> {
      assertNotNull(ref.resolve());
    });
  }

  @Test
  public void testCapitalizedProperty2() {
    PsiReference ref = resolveFixture.get().configureByText("""
                                         class A {
                                           def Prop
                                         }
                                         
                                         new A().pro<caret>p
                                         """);
    ReadAction.runBlocking(() -> {
      assertNotNull(ref.resolve());
    });
  }

  @Test
  public void testCapitalizedProperty3() {
    PsiReference ref = resolveFixture.get().configureByText("""
                                         class A {
                                           def prop
                                         }
                                         
                                         new A().Pro<caret>p
                                         """);
    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }

  @Test
  public void testCapitalizedProperty4() {
    PsiReference ref = resolveFixture.get().configureByText("""
                                         class A {
                                           def prop
                                         }
                                         
                                         new A().pro<caret>p
                                         """);
    ReadAction.runBlocking(() -> {
      assertNotNull(ref.resolve());
    });
  }

  @Test
  public void testGetChars() {
    PsiReference ref = resolveFixture.get().configureByText("""
                                         'abc'.cha<caret>rs
                                         """);
    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrGdkMethod.class, resolved);
      PsiMethod method = ((GrGdkMethod)resolved).getStaticMethod();

      assertEquals("java.lang.CharSequence", method.getParameterList().getParameters()[0].getType().getCanonicalText());
    });
  }

  @Test
  public void testLocalVarNotAvailableInClass() {
    PsiReference ref = resolveFixture.get().configureByText("""
                                         def aa = 5
                                         
                                         class Inner {
                                           def foo() {
                                             print a<caret>a
                                           }
                                         }
                                         """);
    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }

  @Test
  public void testLocalVarNotAvailableInMethod() {
    PsiReference ref = resolveFixture.get().configureByText("""
                                         def aa = 5
                                         
                                         def foo() {
                                           print a<caret>a
                                         }
                                         """);
    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }

  @Test
  public void testScriptFieldNotAvailableInClass() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    LightGroovyFixtures.addGroovyTransformField(myFixture);
    PsiReference ref = resolveFixture.get().configureByText("""
                                         import groovy.transform.Field
                                         
                                         @Field
                                         def aa = 5
                                         
                                         class X {
                                           def foo() {
                                             print a<caret>a
                                           }
                                         }
                                         """);
    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }

  @Test
  public void testInitializerOfScriptField() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    LightGroovyFixtures.addGroovyTransformField(myFixture);
    PsiReference ref = resolveFixture.get().configureByText("""
                                         import groovy.transform.Field
                                         
                                         def xx = 5
                                         @Field
                                         def aa = 5 + x<caret>x
                                         """);
    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }

  @Test
  public void testInitializerOfScriptField2() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    LightGroovyFixtures.addGroovyTransformField(myFixture);
    PsiReference ref = resolveFixture.get().configureByText("""
                                         import groovy.transform.Field
                                         
                                         @Field
                                         def xx = 5
                                         
                                         @Field
                                         def aa = 5 + x<caret>x
                                         """);
    ReadAction.runBlocking(() -> {
      assertInstanceOf(GrVariable.class, ref.resolve());
    });
  }

  @Test
  public void testAliasedImportedPropertyWithGetterInAlias() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("Foo.groovy", """
      class Foo {
        static def prop = 2
      }
      """);

    PsiReference ref = resolveFixture.get().configureByText("""
                                         import static Foo.getProp as getOther
                                         
                                         print othe<caret>r
                                         """);

    ReadAction.runBlocking(() -> {
      assertInstanceOf(GrAccessorMethod.class, ref.resolve());
    });
  }

  @Test
  public void testPrefLocalVarToScriptName() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("foo.groovy", "print 2");

    PsiReference ref = resolveFixture.get().configureByText("""
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

    ReadAction.runBlocking(() -> {
      assertInstanceOf(GrField.class, ref.resolve());
    });
  }

  @Test
  public void testResolveEnumConstantInsideItsInitializer() {
    PsiReference ref = resolveFixture.get().configureByText("""
                                         enum MyEnum {
                                             CONST {
                                                 void get() {
                                                     C<caret>ONST
                                                 }
                                             }
                                         }
                                         """);
    ReadAction.runBlocking(() -> {
      assertNotNull(ref);
    });
  }

  @Test
  public void testSpreadWithIterator() {
    final PsiReference ref = resolveFixture.get().configureByText("""
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

    ReadAction.runBlocking(() -> {
      final PsiMember resolved = assertInstanceOf(PsiMember.class, ref.resolve());
      assertNotNull(resolved.getContainingClass());
      assertEquals("Person", resolved.getContainingClass().getName());
    });
  }

  @Test
  public void testAutoSpread() {
    PsiReference ref = resolveFixture.get().configureByText("""
                                         class A {
                                             String getString() {return "a";}
                                         }
                                         
                                         println ([[new A()]].stri<caret>ng)
                                         """);

    ReadAction.runBlocking(() -> {
      final PsiElement resolved = ref.resolve();

      assertNotNull(resolved);
    });
  }

  @Test
  public void testImportedProperty() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("pack/Const.groovy", """
      package pack
      
      class Const {
        static final int Field1 = 2
      }
      """);
    resolveFixture.get().resolveByText("""
                    import static pack.Const.getField1
                    
                    print Fie<caret>ld1
                    """, null);
  }

  @Test
  public void testImportedProperty2() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("pack/Const.groovy", """
      package pack
      
      class Const {
        static final int Field1 = 2
      }
      """);

    resolveFixture.get().resolveByText("""
                    import static pack.Const.getField1
                    
                    print fie<caret>ld1
                    """, GrAccessorMethod.class);
  }

  @Test
  public void testImportedProperty3() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("pack/Const.groovy", """
      package pack
      
      class Const {
        static final int Field1 = 2
      }
      """);

    resolveFixture.get().resolveByText("""
                    import static pack.Const.getField1 as getBar
                    
                    print Ba<caret>r
                    """, null);
  }

  @Test
  public void testImportedProperty4() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("pack/Const.groovy", """
      package pack
      
      class Const {
        static final int Field1 = 2
      }
      """);

    resolveFixture.get().resolveByText("""
                    import static pack.Const.getField1 as getBar
                    
                    print ba<caret>r
                    """, GrAccessorMethod.class);
  }

  @Test
  public void testImportedProperty5() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("pack/Const.groovy", """
      package pack
      
      class Const {
        static final int field1 = 2
      }
      """);

    resolveFixture.get().resolveByText("""
                    import static pack.Const.getField1
                    
                    print Fie<caret>ld1
                    """, null);
  }

  @Test
  public void testLocalVarVsClassFieldInAnonymous() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    GrVariable resolved = resolveFixture.get().resolveByText("""
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
    ReadAction.runBlocking(() -> {
      assertFalse(resolved instanceof PsiField);
    });
  }

  @Test
  public void testInterfaceDoesNotResolveWithExpressionQualifier() {
    PsiReference ref = resolveFixture.get().configureByText("""
                                         class Foo {
                                           interface Inner {
                                           }
                                         
                                           public Inner = 5
                                         }
                                         
                                         new Foo().Inn<caret>er
                                         """);

    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiField.class, ref.resolve());
    });
  }

  @Test
  public void testInterfaceDoesNotResolveWithExpressionQualifier2() {
    PsiReference ref = resolveFixture.get().configureByText("""
                                         class Foo {
                                           interface Inner {
                                           }
                                         
                                           public Inner = 5
                                         }
                                         
                                         def foo = new Foo()
                                         print foo.Inn<caret>er
                                         """);

    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiField.class, ref.resolve());
    });
  }

  @Test
  public void testResolveBinding1() {
    resolveFixture.get().resolveByText("""
                    abc = 4
                    
                    print ab<caret>c
                    """, GrBindingVariable.class);
  }

  public void ignoreTestResolveBinding2() {
    resolveFixture.get().resolveByText("""
                    print ab<caret>c
                    
                    abc = 4
                    """, GrBindingVariable.class);
  }

  @Test
  public void testResolveBinding3() {
    resolveFixture.get().resolveByText("""
                    a<caret>bc = 4
                    
                    print abc
                    """, GrBindingVariable.class);
  }

  @Test
  public void testResolveBinding4() {
    resolveFixture.get().resolveByText("""
                    print abc
                    
                    a<caret>bc = 4
                    """, GrBindingVariable.class);
  }

  @Test
  public void testResolveBinding5() {
    resolveFixture.get().resolveByText("""
                    def foo() {
                      abc = 4
                    }
                    
                    def bar() {
                      print ab<caret>c
                    }
                    """, null);
  }

  @Test
  public void testResolveBinding6() {
    resolveFixture.get().resolveByText("""
                    def foo() {
                      print ab<caret>c
                    }
                    
                    def bar() {
                      abc = 4
                    }
                    """, null);
  }

  @Test
  public void testResolveBinding7() {
    resolveFixture.get().resolveByText("""
                    def foo() {
                      a<caret>bc = 4
                    }
                    
                    def bar() {
                      print abc
                    }
                    """, null);
  }

  @Test
  public void testResolveBinding8() {
    resolveFixture.get().resolveByText("""
                    def foo() {
                      print abc
                    }
                    
                    def bar() {
                      a<caret>bc = 4
                    }
                    """, null);
  }

  @Test
  public void testBinding9() {
    resolveFixture.get().resolveByText("""
                    a<caret>a = 5
                    print aa
                    aa = 6
                    print aa
                    """, GrBindingVariable.class);
  }

  @Test
  public void testBinding10() {
    resolveFixture.get().resolveByText("""
                    aa = 5
                    print a<caret>a
                    aa = 6
                    print aa
                    """, GrBindingVariable.class);
  }

  @Test
  public void testBinding11() {
    resolveFixture.get().resolveByText("""
                    aa = 5
                    print aa
                    a<caret>a = 6
                    print aa
                    """, GrBindingVariable.class);
  }

  @Test
  public void testBinding12() {
    resolveFixture.get().resolveByText("""
                    aa = 5
                    print aa
                    aa = 6
                    print a<caret>a
                    """, GrBindingVariable.class);
  }

  @Test
  public void testBinding13() {
    resolveFixture.get().resolveByText("""
                    aaa = 1
                    
                    def foo() {
                      aa<caret>a
                    }
                    """, GrBindingVariable.class);
  }

  @Test
  public void testBinding14() {
    resolveFixture.get().resolveByText("""
                    def foo() {
                      aa<caret>a
                    }
                    
                    aaa = 1
                    """, null);
  }

  @Test
  public void testVarVsPackage1() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("package p; public class A {}");
    resolveFixture.get().resolveByText("""
                    def p = [A:5]
                    
                    print <caret>p.A
                    """, PsiPackage.class);
  }

  @Test
  public void testVarVsPackage2() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("package p; public class A {}");
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());

    resolveFixture.get().resolveByText("""
                    def p = [A:5]
                    
                    print <caret>p
                    """, GrVariable.class);
  }

  @Test
  public void testVarVsPackage3() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("package p; public class A {}");
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());

    resolveFixture.get().resolveByText("""
                    def p = [A:{2}]
                    
                    print <caret>p.A()
                    """, GrVariable.class);
  }

  @Test
  public void testVarVsPackage4() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("package p; public class A {public static int foo(){return 2;}}");

    resolveFixture.get().resolveByText("""
                    def p = [A:[foo:{-2}]]
                    
                    print <caret>p.A.foo()
                    """, PsiPackage.class);
  }

  @Test
  public void testVarVsClass1() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("package p; public class A {public static int foo() {return 1;}}");
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());

    resolveFixture.get().resolveByText("""
                    import p.A
                    def A = [a:{-1}]
                    print <caret>A
                    """, GrVariable.class);
  }

  @Test
  public void testVarVsClass2() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("package p; public class A {public static int foo() {return 1;}}");
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());

    resolveFixture.get().resolveByText("""
                    import p.A
                    def A = [a:{-1}]
                    print <caret>A.a()
                    """, GrVariable.class);
  }

  @Test
  public void testTraitPublicField1() {
    resolveFixture.get().resolveByText("""
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

  @Test
  public void testTraitPublicField2() {
    resolveFixture.get().resolveByText("""
                    trait T {
                      public int field = 4
                    
                      void foo() {
                        print fiel<caret>d
                      }
                    }
                    """, GrField.class);
  }

  @Test
  public void testTraitPublicField3() {
    resolveFixture.get().resolveByText("""
                    trait T {
                      public int field = 4
                    
                      void foo() {
                        print T__fie<caret>ld
                      }
                    }
                    """, GrField.class);
  }

  @Test
  public void testTraitPublicField4() {
    resolveFixture.get().resolveByText("""
                    trait T {
                      public int field = 4
                    }
                    
                    class C implements T {}
                    
                    new C().T__fiel<caret>d
                    """, GrField.class);
  }

  @Test
  public void testTraitProperty1() {
    resolveFixture.get().resolveByText("""
                    trait T {
                      int prop = 4
                    }
                    
                    class C extends T {}
                    
                    new C().pr<caret>op
                    """, GrTraitMethod.class);
  }

  @Test
  public void testTraitProperty2() {
    resolveFixture.get().resolveByText("""
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

  @Test
  public void testTraitProperty3() {
    resolveFixture.get().resolveByText("""
                    trait T {
                      int prop = 4
                    
                      void foo() {
                        print pro<caret>p
                      }
                    }
                    """, GrField.class);
  }

  @Test
  public void testTraitPropertyFromAsOperator1() {
    resolveFixture.get().resolveByText("""
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

  @Test
  public void testTraitPropertyFromAsOperator2() {
    resolveFixture.get().resolveByText("""
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

  @Test
  public void testTraitField1() {
    resolveFixture.get().resolveByText("""
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

  @Test
  public void testTraitField2() {
    resolveFixture.get().resolveByText("""
                          trait T {
                            public foo
                    
                            def bar() {
                              print fo<caret>o
                            }
                          }
                    """, PsiField.class);
  }

  @Test
  public void testSetterOverloading() {
    PsiMethod method = resolveFixture.get().resolveByText("""
                                       class A {
                                           void setFoo(File f) {}
                                       
                                           void setFoo(String s) {}
                                       }
                                       
                                       new A().fo<caret>o = ""
                                       """, GrMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("java.lang.String", method.getParameterList().getParameters()[0].getType().getCanonicalText());
    });
  }

  @Test
  public void testOnDemandStaticImport() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("""
                         package somepackage;
                         public class Holder {
                           public static Object getStuff() { return new Object(); }
                         }
                         """);

    PsiMethod method = resolveFixture.get().resolveByText("""
                                       import static somepackage.Holder.*
                                       class Foo {
                                         static field = stu<caret>ff
                                       }
                                       """, PsiMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("getStuff", method.getName());
    });
  }

  @Test
  public void testPreferLocalOverMapKey() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());

    resolveFixture.get().resolveByText("def abc = 42; [:].with { <caret>abc }", GrVariable.class);
  }

  @Test
  public void testClassPropertyVsInstanceProperty() {
    PsiMethod property = resolveFixture.get().resolveByText("""
                                         class A {
                                           int getName() { 42 }
                                         }
                                         
                                         println A.<caret>name
                                         """, PsiMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("java.lang.Class", property.getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void testDontResolveToFieldInMethodCall() {
    resolveFixture.get().resolveByText("""
                    class Fff {
                      List<String> testThis = [""]
                      def usage() { <caret>testThis(1) }
                      def testThis(a) {}
                    }
                    """, GrMethod.class);
  }

  @Test
  public void testStaticFieldViaClassInstance() {
    resolveFixture.get().resolveByText("""
                    class A { public static someStaticField = 42 }
                    def a = A // class instance
                    a.<caret>someStaticField
                    """, GrField.class);
  }

  @Test
  public void testGPathInArray() {
    resolveFixture.get().resolveByText("""
                    class A {
                        int foo
                        A(int x) { foo = x }
                    }
                    
                    A[] ai = [new A(1), new A(2)].toArray()
                    
                    println(ai.fo<caret>o)
                    """, GrAccessorMethod.class);
  }

  @Test
  public void testGPathInArray2() {
    resolveFixture.get().resolveByText("""
                    class A {
                        int length
                        A(int x) { length = x }
                    }
                    
                    A[] ai = [new A(1), new A(2)].toArray()
                    
                    println(ai.len<caret>gth)
                    """, GrField.class);
  }
}
