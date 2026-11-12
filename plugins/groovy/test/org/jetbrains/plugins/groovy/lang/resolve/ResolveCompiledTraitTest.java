// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
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
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.GroovyProjectDescriptors.LIB_GROOVY_LATEST;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolveCompiledTraitTest {

  @NotNull
  private static final TestLibrary SOME_LIBRARY = new TestLibrary() {

    @Override
    public void addTo(@NotNull Module module, @NotNull ModifiableRootModel model) {
      Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("some-library").getModifiableModel();
      JarFileSystem fs = JarFileSystem.getInstance();
      String root = TestUtils.getAbsoluteTestDataPath() + "/lib";
      VfsRootAccess.allowRootAccess(module, root);
      modifiableModel.addRoot(fs.refreshAndFindFileByPath(root + "/some-library.jar!/"),
                              OrderRootType.CLASSES);
      modifiableModel.addRoot(fs.refreshAndFindFileByPath(root + "/some-library-src.jar!/"),
                              OrderRootType.SOURCES);
      modifiableModel.commit();
    }
  };

  @NotNull
  private static final LightProjectDescriptor DESCRIPTOR = new LibraryLightProjectDescriptor(LIB_GROOVY_LATEST.plus(SOME_LIBRARY));
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<GroovyResolveFixture> resolve =
    groovyResolveFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "resolve/");

  @Test
  public void testResolveTrait() {
    resolve.get().resolveByText(
      """
        class ExternalConcrete implements somepackage.T<caret>T {
            @Override
            def someAbstractMethod() {}
            @Override
            void anotherInterfaceMethod() {}
        }
        """, ClsClassImpl.class);
  }

  @Test
  public void testMethodImplementedInTrait() {
    resolve.get().resolveByText(
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

  @Test
  public void testMethodFromInterfaceImplementedInTrait() {
    resolve.get().resolveByText(
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

  @Test
  public void testStaticTraitMethod() {
    resolve.get().resolveByText(
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

  @Test
  public void testTraitField() {
    resolve.get().resolveByText(
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

  @Test
  public void testStaticTraitField() {
    resolve.get().resolveByText(
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

  @Test
  public void testTraitFieldFullName() {
    resolve.get().resolveByText(
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

  @Test
  public void testStaticTraitFieldFullName() {
    resolve.get().resolveByText(
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

  @Test
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

  @Test
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

  @Test
  public void testNotImplementedCompiledTraitMethod() {
    testHighlighting(
      """
        <error descr="Method 'someAbstractMethod' is not implemented">class ExternalConcrete implements somepackage.TT</error> {
            @Override
            void anotherInterfaceMethod() {}
        }""");
  }

  @Test
  public void testNotImplementedCompiledInterfaceMethodWithTraitInHierarchy() {
    testHighlighting(
      """
        <error descr="Method 'anotherInterfaceMethod' is not implemented">class ExternalConcrete implements somepackage.TT</error> {
            @Override
            def someAbstractMethod() {}
        }""");
  }

  @Test
  public void testTraitParameterNotWithinItsBounds() {
    testHighlighting(
      """
        class ExternalConcrete2 implements somepackage.GenericTrait<String, somepackage.Pojo, <warning descr="Type parameter 'java.lang.Integer' is not in its bound; should extend 'A'">Integer</warning>> {}
        """);
  }

  @Test
  public void testGenericTraitMethod() {
    PsiClass definition = configureTraitInheritor();
    ReadAction.runBlocking(() -> {
      PsiMethod method = definition.findMethodsByName("methodWithTraitGenerics", false)[0];
      assertEquals(0, method.getTypeParameters().length);
      assertEquals("Pojo", method.getReturnType().getCanonicalText());
      assertEquals(2, method.getParameterList().getParametersCount());
      assertEquals("java.lang.String", method.getParameterList().getParameters()[0].getType().getCanonicalText());
      assertEquals("PojoInheritor", method.getParameterList().getParameters()[1].getType().getCanonicalText());
    });
  }

  @Test
  public void testGenericTraitMethodWithTypeParameters() {
    configureTraitInheritor();
    GrReferenceExpression reference =
      resolve.get().configureByText("foo.groovy", "new ExternalConcrete().<Integer>methodWit<caret>hMethodGenerics(1, \"2\", null)",
                      GrReferenceExpression.class);
    ReadAction.runBlocking(() -> {
      GroovyResolveResult resolved = reference.advancedResolve();
      PsiMethod method = (PsiMethod)resolved.getElement();
      PsiSubstitutor substitutor = resolved.getSubstitutor();
      assertEquals(1, method.getTypeParameterList().getTypeParameters().length);
      assertEquals("java.lang.Integer", substitutor.substitute(method.getReturnType()).getCanonicalText());
      assertEquals(3, method.getParameterList().getParametersCount());
      assertEquals("java.lang.Integer",
                          substitutor.substitute(method.getParameterList().getParameters()[0].getType()).getCanonicalText());
      assertEquals("java.lang.String", method.getParameterList().getParameters()[1].getType().getCanonicalText());
      assertEquals("PojoInheritor", method.getParameterList().getParameters()[2].getType().getCanonicalText());
    });
  }

  @Test
  public void testGenericTraitMethodTypeParametersClashing() {
    configureTraitInheritor();
    GrReferenceExpression reference =
      resolve.get().configureByText("foo.groovy", "new ExternalConcrete().<Integer>methodWith<caret>MethodGenericsClashing(1,\"\", new PojoInheritor())",
                      GrReferenceExpression.class);
    ReadAction.runBlocking(() -> {
      GroovyResolveResult resolved = reference.advancedResolve();
      PsiMethod method = (PsiMethod)resolved.getElement();
      PsiSubstitutor substitutor = resolved.getSubstitutor();
      assertEquals(1, method.getTypeParameterList().getTypeParameters().length);
      assertEquals("java.lang.Integer", substitutor.substitute(method.getReturnType()).getCanonicalText());
      assertEquals(3, method.getParameterList().getParametersCount());
      assertEquals("java.lang.Integer",
                          substitutor.substitute(method.getParameterList().getParameters()[0].getType()).getCanonicalText());
      assertEquals("java.lang.String", method.getParameterList().getParameters()[1].getType().getCanonicalText());
      assertEquals("PojoInheritor", method.getParameterList().getParameters()[2].getType().getCanonicalText());
    });
  }

  @Test
  public void testGenericTraitStaticMethod() {
    PsiClass definition = configureTraitInheritor();
    ReadAction.runBlocking(() -> {
      PsiMethod method = definition.findMethodsByName("staticMethodWithTraitGenerics", false)[0];
      assertEquals(0, method.getTypeParameters().length);
      assertEquals("Pojo", method.getReturnType().getCanonicalText());
      assertEquals(2, method.getParameterList().getParametersCount());
      assertEquals("java.lang.String", method.getParameterList().getParameters()[0].getType().getCanonicalText());
      assertEquals("PojoInheritor", method.getParameterList().getParameters()[1].getType().getCanonicalText());
    });
  }

  @Test
  public void testGenericTraitStaticMethodWithTypeParameters() {
    configureTraitInheritor();
    GrReferenceExpression reference =
      resolve.get().configureByText("foo.groovy", "new ExternalConcrete().<Integer>staticMethodWit<caret>hMethodGenerics(1, \"2\", null)",
                      GrReferenceExpression.class);
    ReadAction.runBlocking(() -> {
      GroovyResolveResult resolved = reference.advancedResolve();
      PsiMethod method = (PsiMethod)resolved.getElement();
      PsiSubstitutor substitutor = resolved.getSubstitutor();
      assertEquals(1, method.getTypeParameterList().getTypeParameters().length);
      assertEquals("java.lang.Integer", substitutor.substitute(method.getReturnType()).getCanonicalText());
      assertEquals(3, method.getParameterList().getParametersCount());
      assertEquals("java.lang.Integer",
                          substitutor.substitute(method.getParameterList().getParameters()[0].getType()).getCanonicalText());
      assertEquals("java.lang.String", method.getParameterList().getParameters()[1].getType().getCanonicalText());
      assertEquals("PojoInheritor", method.getParameterList().getParameters()[2].getType().getCanonicalText());
    });
  }

  @Test
  public void testGenericTraitStaticMethodTypeParametersClashing() {
    configureTraitInheritor();
    GrReferenceExpression reference = resolve.get().configureByText("foo.groovy",
                                                      "new ExternalConcrete().<Integer>staticMethodWith<caret>MethodGenericsClashing(1, \"\", new PojoInheritor())",
                                                      GrReferenceExpression.class);
    ReadAction.runBlocking(() -> {
      GroovyResolveResult resolved = reference.advancedResolve();
      PsiMethod method = (PsiMethod)resolved.getElement();
      PsiSubstitutor substitutor = resolved.getSubstitutor();
      assertEquals(1, method.getTypeParameterList().getTypeParameters().length);
      assertEquals("java.lang.Integer", substitutor.substitute(method.getReturnType()).getCanonicalText());
      assertEquals(3, method.getParameterList().getParametersCount());
      assertEquals("java.lang.Integer",
                          substitutor.substitute(method.getParameterList().getParameters()[0].getType()).getCanonicalText());
      assertEquals("java.lang.String", method.getParameterList().getParameters()[1].getType().getCanonicalText());
      assertEquals("PojoInheritor", method.getParameterList().getParameters()[2].getType().getCanonicalText());
    });
  }

  @Test
  public void testDoNotResolvePrivateTraitMethod() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.enableInspections(GrUnresolvedAccessInspection.class, GroovyAccessibilityInspection.class);
    testHighlighting("""
                       import privateTraitMethods.C
                       import privateTraitMethods.T
                       
                       def foo(T t) {
                         t.<warning descr="Cannot resolve symbol 'privateMethod'">privateMethod</warning>() // via interface
                       }
                       
                       new C().<warning descr="Cannot resolve symbol 'privateMethod'">privateMethod</warning>() // via implementation
                       """);
  }

  @Test
  public void testDoNotGetMirrorInCompletion() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    ThrowingDecompiler.disableDecompilers(myFixture.getTestRootDisposable());
    myFixture.configureByText("_.groovy",
                                 """
                                   class CC implements somepackage.TT {
                                     def foo() {
                                       someMet<caret>
                                     }
                                   }
                                   """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.completeBasic();
      assertThat(myFixture.getLookupElementStrings()).contains("someMethod", "someAbstractMethod", "someStaticMethod");
    });
  }

  @Test
  public void testStaticTraitMethodWithDelegatesToType() {
    configureTraitInheritor();
    PsiMethod method = resolve.get().resolveByText(
      """
        def usage(ExternalConcrete ec) {
          ec.delegatesTo { <caret>toUpperCase() }
        }
        """, PsiMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("toUpperCase", method.getName());
      assertEquals("java.lang.String", method.getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void testStaticTraitMethodWithClosureParamsFromString() {
    configureTraitInheritor();
    PsiMethod method = resolve.get().resolveByText("""
                                       def usage(ExternalConcrete ec) {
                                         ec.closureParams { it.<caret>toUpperCase() }
                                       }
                                       """, PsiMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("toUpperCase", method.getName());
      assertEquals("java.lang.String", method.getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void testStaticTraitMethodWithDelegatesToTypeOnLambda() {
    configureTraitInheritor();
    PsiMethod method = resolve.get().resolveByText("""
                                       def usage(ExternalConcrete ec) {
                                         ec.delegatesTo () -> <caret>toUpperCase()
                                       }
                                       """, PsiMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("toUpperCase", method.getName());
      assertEquals("java.lang.String", method.getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void testStaticTraitMethodWithClosureParamsFromStringOnLambda() {
    configureTraitInheritor();
    PsiMethod method = resolve.get().resolveByText("""
                                       def usage(ExternalConcrete ec) {
                                         ec.closureParams (it) -> it.<caret>toUpperCase()
                                       }
                                       """, PsiMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("toUpperCase", method.getName());
      assertEquals("java.lang.String", method.getContainingClass().getQualifiedName());
    });
  }

  private PsiClass configureTraitInheritor() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addFileToProject("inheritors.groovy", """
      class PojoInheritor extends somepackage.Pojo {}
      class ExternalConcrete implements somepackage.GenericTrait<Pojo, String, PojoInheritor> {}
      """);
    return ReadAction.computeBlocking(() -> {
      return myFixture.findClass("ExternalConcrete");
    });
  }

  private void testHighlighting(String text) {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, text);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.testHighlighting(true, false, true);
    });
  }

  @Test
  public void testTraitWithDelegate() {
    PsiElement resolved = resolve.get().resolveByText(
      """
        class Impl implements delegation.T {
          void usage() {
            <caret>hi()
          }
        }
        """);
    ReadAction.runBlocking(() -> {
      assertTrue(resolved instanceof DelegatedMethod);
    });
  }
}
