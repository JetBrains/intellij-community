// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor;
import org.jetbrains.plugins.groovy.TestLibrary;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.resolve.ast.DelegatedMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.jetbrains.plugins.groovy.util.ThrowingDecompiler;
import org.junit.Assert;

import static org.jetbrains.plugins.groovy.GroovyProjectDescriptors.LIB_GROOVY_LATEST;

public class ResolveCompiledTraitTest extends GroovyResolveTestCase {

  @Override
  public final String getBasePath() {
    return "resolve/";
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  @NotNull
  private static final TestLibrary SOME_LIBRARY = new TestLibrary() {

    @Override
    public void addTo(@NotNull Module module, @NotNull ModifiableRootModel model) {
      Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("some-library").getModifiableModel();
      JarFileSystem fs = JarFileSystem.getInstance();
      String root = TestUtils.getAbsoluteTestDataPath() + "/lib";
      modifiableModel.addRoot(fs.refreshAndFindFileByPath(root + "/some-library.jar!/"),
                              OrderRootType.CLASSES);
      modifiableModel.addRoot(fs.refreshAndFindFileByPath(root + "/some-library-src.jar!/"),
                              OrderRootType.SOURCES);
      modifiableModel.commit();
    }
  };

  @NotNull
  private static final LightProjectDescriptor DESCRIPTOR = new LibraryLightProjectDescriptor(LIB_GROOVY_LATEST.plus(SOME_LIBRARY));

  @NotNull
  private final LightProjectDescriptor projectDescriptor = DESCRIPTOR;

  public void testResolveTrait() {
    resolveByText(
      """
        class ExternalConcrete implements somepackage.T<caret>T {
            @Override
            def someAbstractMethod() {}
            @Override
            void anotherInterfaceMethod() {}
        }
        """, ClsClassImpl.class);
  }

  public void testMethodImplementedInTrait() {
    resolveByText(
      """
        class ExternalConcrete implements somepackage.TT {
            @Override
            def someAbstractMethod() {}
            @Override
            void anotherInterfaceMethod() {}
        }
        new ExternalConcrete().som<caret>eMethod()
        """, GrTraitMethod.class);
  }

  public void testMethodFromInterfaceImplementedInTrait() {
    resolveByText(
      """
        class ExternalConcrete implements somepackage.TT {
            @Override
            def someAbstractMethod() {}
            @Override
            void anotherInterfaceMethod() {}
        }
        new ExternalConcrete().interface<caret>Method()
        """, GrTraitMethod.class);
  }

  public void testStaticTraitMethod() {
    resolveByText(
      """
        class ExternalConcrete implements somepackage.TT {
            @Override
            def someAbstractMethod() {}
            @Override
            void anotherInterfaceMethod() {}
        }
        ExternalConcrete.someStatic<caret>Method()
        """, GrTraitMethod.class);
  }

  public void testTraitField() {
    resolveByText(
      """
        class ExternalConcrete implements somepackage.TT {
            @Override
            def someAbstractMethod() {}
            @Override
            void anotherInterfaceMethod() {}
        }
        ExternalConcrete.some<caret>Field
        """, GrTraitMethod.class);
  }

  public void testStaticTraitField() {
    resolveByText(
      """
        class ExternalConcrete implements somepackage.TT {
            @Override
            def someAbstractMethod() {}
            @Override
            void anotherInterfaceMethod() {}
        }
        ExternalConcrete.someStatic<caret>Field
        """, GrTraitMethod.class);
  }

  public void testTraitFieldFullName() {
    resolveByText(
      """
        class ExternalConcrete implements somepackage.TT {
            @Override
            def someAbstractMethod() {}
            @Override
            void anotherInterfaceMethod() {}
        }
        ExternalConcrete.somepackage_<caret>TT__someField
        """, GrTraitField.class);
  }

  public void testStaticTraitFieldFullName() {
    resolveByText(
      """
        class ExternalConcrete implements somepackage.TT {
            @Override
            def someAbstractMethod() {}
            @Override
            void anotherInterfaceMethod() {}
        }
        ExternalConcrete.somepackage_TT_<caret>_someStaticField
        """, GrTraitField.class);
  }

  public void testHighlightingNoErrors() {
    testHighlighting(
      """
        class ExternalConcrete implements somepackage.TT {
            @Override
            def someAbstractMethod() {}
            @Override
            void anotherInterfaceMethod() {}
        }
        """);
  }

  public void testCompiledTraitNoErrors() {
    testHighlighting(
      """
        class ExternalConcrete implements somepackage.TT {
            @Override
            def someAbstractMethod() {}
            @Override
            void anotherInterfaceMethod() {}
        }""");
  }

  public void testNotImplementedCompiledTraitMethod() {
    testHighlighting(
      """
        <error descr="Method 'someAbstractMethod' is not implemented">class ExternalConcrete implements somepackage.TT</error> {
            @Override
            void anotherInterfaceMethod() {}
        }""");
  }

  public void testNotImplementedCompiledInterfaceMethodWithTraitInHierarchy() {
    testHighlighting(
      """
        <error descr="Method 'anotherInterfaceMethod' is not implemented">class ExternalConcrete implements somepackage.TT</error> {
            @Override
            def someAbstractMethod() {}
        }""");
  }

  public void testTraitParameterNotWithinItsBounds() {
    testHighlighting(
      """
        class ExternalConcrete2 implements somepackage.GenericTrait<String, somepackage.Pojo, <warning descr="Type parameter 'java.lang.Integer' is not in its bound; should extend 'A'">Integer</warning>> {}
        """);
  }

  public void testGenericTraitMethod() {
    PsiClass definition = configureTraitInheritor();
    PsiMethod method = definition.findMethodsByName("methodWithTraitGenerics", false)[0];
    Assert.assertEquals(0, method.getTypeParameters().length);
    Assert.assertEquals("Pojo", method.getReturnType().getCanonicalText());
    Assert.assertEquals(2, method.getParameterList().getParametersCount());
    Assert.assertEquals("java.lang.String", method.getParameterList().getParameters()[0].getType().getCanonicalText());
    Assert.assertEquals("PojoInheritor", method.getParameterList().getParameters()[1].getType().getCanonicalText());
  }

  public void testGenericTraitMethodWithTypeParameters() {
    configureTraitInheritor();
    GrReferenceExpression reference =
      configureByText("foo.groovy", "new ExternalConcrete().<Integer>methodWit<caret>hMethodGenerics(1, \"2\", null)",
                      GrReferenceExpression.class);
    GroovyResolveResult resolved = reference.advancedResolve();
    PsiMethod method = (PsiMethod)resolved.getElement();
    PsiSubstitutor substitutor = resolved.getSubstitutor();
    Assert.assertEquals(1, method.getTypeParameterList().getTypeParameters().length);
    Assert.assertEquals("java.lang.Integer", substitutor.substitute(method.getReturnType()).getCanonicalText());
    Assert.assertEquals(3, method.getParameterList().getParametersCount());
    Assert.assertEquals("java.lang.Integer",
                        substitutor.substitute(method.getParameterList().getParameters()[0].getType()).getCanonicalText());
    Assert.assertEquals("java.lang.String", method.getParameterList().getParameters()[1].getType().getCanonicalText());
    Assert.assertEquals("PojoInheritor", method.getParameterList().getParameters()[2].getType().getCanonicalText());
  }

  public void testGenericTraitMethodTypeParametersClashing() {
    configureTraitInheritor();
    GrReferenceExpression reference =
      configureByText("foo.groovy", "new ExternalConcrete().<Integer>methodWith<caret>MethodGenericsClashing(1,\"\", new PojoInheritor())",
                      GrReferenceExpression.class);
    GroovyResolveResult resolved = reference.advancedResolve();
    PsiMethod method = (PsiMethod)resolved.getElement();
    PsiSubstitutor substitutor = resolved.getSubstitutor();
    Assert.assertEquals(1, method.getTypeParameterList().getTypeParameters().length);
    Assert.assertEquals("java.lang.Integer", substitutor.substitute(method.getReturnType()).getCanonicalText());
    Assert.assertEquals(3, method.getParameterList().getParametersCount());
    Assert.assertEquals("java.lang.Integer",
                        substitutor.substitute(method.getParameterList().getParameters()[0].getType()).getCanonicalText());
    Assert.assertEquals("java.lang.String", method.getParameterList().getParameters()[1].getType().getCanonicalText());
    Assert.assertEquals("PojoInheritor", method.getParameterList().getParameters()[2].getType().getCanonicalText());
  }

  public void testGenericTraitStaticMethod() {
    PsiClass definition = configureTraitInheritor();
    PsiMethod method = definition.findMethodsByName("staticMethodWithTraitGenerics", false)[0];
    Assert.assertEquals(0, method.getTypeParameters().length);
    Assert.assertEquals("Pojo", method.getReturnType().getCanonicalText());
    Assert.assertEquals(2, method.getParameterList().getParametersCount());
    Assert.assertEquals("java.lang.String", method.getParameterList().getParameters()[0].getType().getCanonicalText());
    Assert.assertEquals("PojoInheritor", method.getParameterList().getParameters()[1].getType().getCanonicalText());
  }

  public void testGenericTraitStaticMethodWithTypeParameters() {
    configureTraitInheritor();
    GrReferenceExpression reference =
      configureByText("foo.groovy", "new ExternalConcrete().<Integer>staticMethodWit<caret>hMethodGenerics(1, \"2\", null)",
                      GrReferenceExpression.class);
    GroovyResolveResult resolved = reference.advancedResolve();
    PsiMethod method = (PsiMethod)resolved.getElement();
    PsiSubstitutor substitutor = resolved.getSubstitutor();
    Assert.assertEquals(1, method.getTypeParameterList().getTypeParameters().length);
    Assert.assertEquals("java.lang.Integer", substitutor.substitute(method.getReturnType()).getCanonicalText());
    Assert.assertEquals(3, method.getParameterList().getParametersCount());
    Assert.assertEquals("java.lang.Integer",
                        substitutor.substitute(method.getParameterList().getParameters()[0].getType()).getCanonicalText());
    Assert.assertEquals("java.lang.String", method.getParameterList().getParameters()[1].getType().getCanonicalText());
    Assert.assertEquals("PojoInheritor", method.getParameterList().getParameters()[2].getType().getCanonicalText());
  }

  public void testGenericTraitStaticMethodTypeParametersClashing() {
    configureTraitInheritor();
    GrReferenceExpression reference = configureByText("foo.groovy",
                                                      "new ExternalConcrete().<Integer>staticMethodWith<caret>MethodGenericsClashing(1, \"\", new PojoInheritor())",
                                                      GrReferenceExpression.class);
    GroovyResolveResult resolved = reference.advancedResolve();
    PsiMethod method = (PsiMethod)resolved.getElement();
    PsiSubstitutor substitutor = resolved.getSubstitutor();
    Assert.assertEquals(1, method.getTypeParameterList().getTypeParameters().length);
    Assert.assertEquals("java.lang.Integer", substitutor.substitute(method.getReturnType()).getCanonicalText());
    Assert.assertEquals(3, method.getParameterList().getParametersCount());
    Assert.assertEquals("java.lang.Integer",
                        substitutor.substitute(method.getParameterList().getParameters()[0].getType()).getCanonicalText());
    Assert.assertEquals("java.lang.String", method.getParameterList().getParameters()[1].getType().getCanonicalText());
    Assert.assertEquals("PojoInheritor", method.getParameterList().getParameters()[2].getType().getCanonicalText());
  }

  public void testDoNotResolvePrivateTraitMethod() {
    getFixture().enableInspections(GrUnresolvedAccessInspection.class, GroovyAccessibilityInspection.class);
    testHighlighting("""
                       import privateTraitMethods.C
                       import privateTraitMethods.T
                       
                       def foo(T t) {
                         t.<warning descr="Cannot resolve symbol 'privateMethod'">privateMethod</warning>() // via interface
                       }
                       
                       new C().<warning descr="Cannot resolve symbol 'privateMethod'">privateMethod</warning>() // via implementation
                       """);
  }

  public void testDoNotGetMirrorInCompletion() {
    ThrowingDecompiler.disableDecompilers(getTestRootDisposable());
    getFixture().configureByText("_.groovy",
                                 """
                                   class CC implements somepackage.TT {
                                     def foo() {
                                       someMet<caret>
                                     }
                                   }
                                   """);
    getFixture().completeBasic();
    UsefulTestCase.assertContainsElements(getFixture().getLookupElementStrings(), "someMethod", "someAbstractMethod", "someStaticMethod");
  }

  public void testStaticTraitMethodWithDelegatesToType() {
    configureTraitInheritor();
    PsiMethod method = resolveByText(
      """
        def usage(ExternalConcrete ec) {
          ec.delegatesTo { <caret>toUpperCase() }
        }
        """, PsiMethod.class);
    Assert.assertEquals("toUpperCase", method.getName());
    Assert.assertEquals("java.lang.String", method.getContainingClass().getQualifiedName());
  }

  public void testStaticTraitMethodWithClosureParamsFromString() {
    configureTraitInheritor();
    PsiMethod method = resolveByText("""
                                       def usage(ExternalConcrete ec) {
                                         ec.closureParams { it.<caret>toUpperCase() }
                                       }
                                       """, PsiMethod.class);
    Assert.assertEquals("toUpperCase", method.getName());
    Assert.assertEquals("java.lang.String", method.getContainingClass().getQualifiedName());
  }

  public void testStaticTraitMethodWithDelegatesToTypeOnLambda() {
    configureTraitInheritor();
    PsiMethod method = resolveByText("""
                                       def usage(ExternalConcrete ec) {
                                         ec.delegatesTo () -> <caret>toUpperCase()
                                       }
                                       """, PsiMethod.class);
    Assert.assertEquals("toUpperCase", method.getName());
    Assert.assertEquals("java.lang.String", method.getContainingClass().getQualifiedName());
  }

  public void testStaticTraitMethodWithClosureParamsFromStringOnLambda() {
    configureTraitInheritor();
    PsiMethod method = resolveByText("""
                                       def usage(ExternalConcrete ec) {
                                         ec.closureParams (it) -> it.<caret>toUpperCase()
                                       }
                                       """, PsiMethod.class);
    Assert.assertEquals("toUpperCase", method.getName());
    Assert.assertEquals("java.lang.String", method.getContainingClass().getQualifiedName());
  }

  private PsiClass configureTraitInheritor() {
    myFixture.addFileToProject("inheritors.groovy", """
      class PojoInheritor extends somepackage.Pojo {}
      class ExternalConcrete implements somepackage.GenericTrait<Pojo, String, PojoInheritor> {}
      """);
    return myFixture.findClass("ExternalConcrete");
  }

  private void testHighlighting(String text) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, text);
    myFixture.testHighlighting(true, false, true);
  }

  public void testTraitWithDelegate() {
    PsiElement resolved = resolveByText(
      """
        class Impl implements delegation.T {
          void usage() {
            <caret>hi()
          }
        }
        """);
    Assert.assertTrue(resolved instanceof DelegatedMethod);
  }
}
