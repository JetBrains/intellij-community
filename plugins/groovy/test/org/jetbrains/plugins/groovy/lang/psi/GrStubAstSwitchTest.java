// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.testFramework.BombedProgressIndicator;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ref.GCWatcher;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrAnonymousClassDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnonymousClassIndex;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GrStubAstSwitchTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testDontLoadContentWhenProcessingImports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFileImpl file = (GroovyFileImpl)myFixture.addFileToProject("A.groovy", """
      import java.util.concurrent.ConcurrentHashMap
      
      class MyMap extends ConcurrentHashMap {}
      class B extends ConcurrentHashMap {
        void foo() {
          print 4
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      assertFalse(file.isContentsLoaded());
      PsiClass bClass = file.getClasses()[1];
      assertFalse(file.isContentsLoaded());

      PsiMethod fooMethod = bClass.getMethods()[0];
      assertFalse(file.isContentsLoaded());

      fooMethod.findDeepestSuperMethods();
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void testDontLoadAstForAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFileImpl file = DefaultGroovyMethods.asType(myFixture.addFileToProject("a.groovy", """
      class A {
        def foo(){}
      }
      
      class B {
        @Delegate
        A a = new A()
      }
      """), GroovyFileImpl.class);

    ReadAction.runBlocking(() -> {
      assertFalse(file.isContentsLoaded());
      PsiClass clazzB = file.getClasses()[1];
      assertFalse(file.isContentsLoaded());

      PsiField field = clazzB.getFields()[0];
      assertFalse(file.isContentsLoaded());

      PsiModifierList modifierList = field.getModifierList();
      assertFalse(file.isContentsLoaded());

      PsiAnnotation[] annotations = modifierList.getAnnotations();
      PsiAnnotation annotation = annotations[0];
      assertFalse(file.isContentsLoaded());

      assertEquals("groovy.lang.Delegate", annotation.getQualifiedName());
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void testDontLoadAstForAnnotation2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFileImpl file = DefaultGroovyMethods.asType(myFixture.addFileToProject("a.groovy", """
      class A {
        def foo(){}
      }
      
      class B extends A {
        @Override
        def foo() {}
      }
      """), GroovyFileImpl.class);

    ReadAction.runBlocking(() -> {
      assertFalse(file.isContentsLoaded());
      PsiClass clazzB = file.getClasses()[1];
      assertFalse(file.isContentsLoaded());

      PsiMethod method = clazzB.getMethods()[0];
      assertFalse(file.isContentsLoaded());

      PsiModifierList modifierList = method.getModifierList();
      assertFalse(file.isContentsLoaded());

      PsiAnnotation[] annotations = modifierList.getAnnotations();
      PsiAnnotation annotation = annotations[0];
      assertFalse(file.isContentsLoaded());

      assertEquals("java.lang.Override", annotation.getQualifiedName());
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void testDelegateExists() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFileImpl file = DefaultGroovyMethods.asType(myFixture.addFileToProject("a.groovy", """
      class A {
        def foo(){}
      }
      
      class B {
        @Delegate
        A a = new A()
      }
      """), GroovyFileImpl.class);

    ReadAction.runBlocking(() -> {
      assertFalse(file.isContentsLoaded());
      PsiClass clazzB = file.getClasses()[1];
      assertFalse(file.isContentsLoaded());
      assertTrue(ContainerUtil.exists(clazzB.getMethods(), method -> method.getName().equals("foo")));
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void testDefaultValueForAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("pack/Ann.groovy", """
      package pack
      
      @interface Ann {
          String foo() default 'def'
      }
      """);

    GroovyFileImpl file = DefaultGroovyMethods.asType(myFixture.addFileToProject("usage.groovy", """
      import pack.Ann
      
      class X {
        @Ann()
        String bar() {}
      }
      """), GroovyFileImpl.class);

    ReadAction.runBlocking(() -> {
      assertFalse(file.isContentsLoaded());
      PsiClass clazz = file.getClasses()[0];
      assertFalse(file.isContentsLoaded());
      PsiMethod method = clazz.getMethods()[0];
      assertFalse(file.isContentsLoaded());
      PsiAnnotation annotation = method.getModifierList().findAnnotation("pack.Ann");
      assertFalse(file.isContentsLoaded());
      assertNotNull(annotation.findAttributeValue("foo"));
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void testDefaultValueForAnnotationWithAliases() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("pack/Ann.groovy", """
      package pack
      
      @interface Ann {
          String foo() default 'def'
      }
      """);

    GroovyFileImpl file = DefaultGroovyMethods.asType(myFixture.addFileToProject("usage.groovy", """
      import pack.Ann as A
      
      class X {
        @A()
        String bar() {}
      }
      """), GroovyFileImpl.class);

    ReadAction.runBlocking(() -> {
      assertFalse(file.isContentsLoaded());
      PsiClass clazz = file.getClasses()[0];
      assertFalse(file.isContentsLoaded());
      PsiMethod method = clazz.getMethods()[0];
      assertFalse(file.isContentsLoaded());
      PsiAnnotation annotation = method.getModifierList().findAnnotation("pack.Ann");
      assertFalse(file.isContentsLoaded());
      assertNotNull(annotation.findAttributeValue("foo"));
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void testValueForAnnotationWithAliases() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("pack/Ann.groovy", """
      package pack
      
      @interface Ann {
          String foo() default 'def'
      }
      """);

    GroovyFileImpl file = DefaultGroovyMethods.asType(myFixture.addFileToProject("usage.groovy", """
      import pack.Ann as A
      
      class X {
        @A(foo='non_def')
        String bar() {}
      }
      """), GroovyFileImpl.class);

    ReadAction.runBlocking(() -> {
      assertFalse(file.isContentsLoaded());
      PsiClass clazz = file.getClasses()[0];
      assertFalse(file.isContentsLoaded());
      PsiMethod method = clazz.getMethods()[0];
      assertFalse(file.isContentsLoaded());
      PsiAnnotation annotation = method.getModifierList().findAnnotation("pack.Ann");
      assertFalse(file.isContentsLoaded());
      assertNotNull(annotation.findAttributeValue("foo"));
      assertTrue(file.isContentsLoaded());
    });
  }

  @Test
  public void test_do_not_load_ast_for_annotation_reference_value() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFileImpl file = DefaultGroovyMethods.asType(myFixture.addFileToProject("Pogo.groovy", """
      @groovy.transform.AutoClone(style=groovy.transform.AutoCloneStyle.SIMPLE)
      class Pogo {}\s
      """), GroovyFileImpl.class);
    ReadAction.runBlocking(() -> {
      assertFalse(file.isContentsLoaded());
      PsiClass clazz = file.getClasses()[0];
      assertFalse(file.isContentsLoaded());
      PsiMethod method = ContainerUtil.find(clazz.getMethods(), m -> m.getName().equals("cloneOrCopyMembers"));
      assertNotNull(method);
      assertFalse(file.isContentsLoaded());
      assertTrue(method.hasModifierProperty(PsiModifier.PROTECTED));
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void test_do_not_load_content_for_findMethodsByName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFileImpl file = DefaultGroovyMethods.asType(myFixture.addFileToProject("usage.groovy", """
      class X {
        void foo(int a, int b = 2) {}
      }
      """), GroovyFileImpl.class);
    ReadAction.runBlocking(() -> {
      assertFalse(file.isContentsLoaded());
      PsiClass clazz = file.getClasses()[0];
      assertFalse(file.isContentsLoaded());

      assertEquals(2, DefaultGroovyMethods.size(clazz.findMethodsByName("foo", false)));
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void test_do_not_load_content_for_anonymous_class__baseClassType() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = myFixture.getTempDirFixture().createFile("A.groovy", """
      class A {
        def field = new Runnable() {
          void run() {}
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiFileImpl psiFile = DefaultGroovyMethods.asType(PsiManager.getInstance(myFixture.getProject()).findFile(file), PsiFileImpl.class);
      assertNotNull(psiFile.getStub());
      assertFalse(psiFile.isContentsLoaded());

      final Collection<GrAnonymousClassDefinition> classes =
        StubIndex.getElements(GrAnonymousClassIndex.KEY, "Runnable", myFixture.getProject(), GlobalSearchScope.allScope(myFixture.getProject()),
                              GrAnonymousClassDefinition.class);
      assertEquals(1, classes.size());

      GrAnonymousClassDefinition definition = DefaultGroovyMethods.first(classes);
      assertNotNull(((GrAnonymousClassDefinitionImpl)definition).getStub());
      assertNotNull(psiFile.getStub());
      assertFalse(psiFile.isContentsLoaded());

      definition.getBaseClassType();
      assertNotNull(psiFile.getStub());
      assertFalse(psiFile.isContentsLoaded());

      assertTrue(InheritanceUtil.isInheritor(definition, Runnable.class.getName()));
      assertNotNull(psiFile.getStub());
      assertFalse(psiFile.isContentsLoaded());
    });
  }

  @Test
  public void test_do_not_load_contents_in_highlighting() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final VirtualFile file = myFixture.getTempDirFixture().createFile("classes.groovy", """
      class C {
        static void staticVoidMethod(a, b = 1) {}
      }
      """);
    ((JavaCodeInsightTestFixtureImpl)myFixture).setVirtualFileFilter(f -> f.equals(file));
    myFixture.configureByText("_.groovy", "C.staticVoidMethod(1)");
    myFixture.checkHighlighting();
  }

  @Test
  public void test_do_not_load_AST_when_method_has_no_comment() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = myFixture.getTempDirFixture().createFile("classes.groovy", """
      class C {
        static void someMethod() {}
        /**
        *
        */
        static void someMethodWithDocs() {}
      }
      """);
    ReadAction.runBlocking(() -> {
      GroovyFileImpl psiFile = DefaultGroovyMethods.asType(PsiManager.getInstance(myFixture.getProject()).findFile(file), GroovyFileImpl.class);
      assertFalse(psiFile.isContentsLoaded());

      GrTypeDefinition typeDefinition = DefaultGroovyMethods.first(psiFile.getTypeDefinitions());
      assertFalse(psiFile.isContentsLoaded());

      PsiMethod method = DefaultGroovyMethods.first(typeDefinition.findMethodsByName("someMethod", false));
      assertFalse(psiFile.isContentsLoaded());

      assertNull(method.getDocComment());
      assertFalse(psiFile.isContentsLoaded());

      PsiMethod methodWithDocs = DefaultGroovyMethods.first(typeDefinition.findMethodsByName("someMethodWithDocs", false));
      assertFalse(psiFile.isContentsLoaded());

      assertNotNull(methodWithDocs.getDocComment());
      assertTrue(psiFile.isContentsLoaded());
    });
  }

  @Test
  public void test_type_parameters_bounds() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFileImpl file = (GroovyFileImpl) myFixture.addFileToProject("classes.groovy", "class C<T extends Runnable> {}");
    ReadAction.runBlocking(() -> {
      ((PsiManagerEx)PsiManager.getInstance(myFixture.getProject())).setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, myFixture.getTestRootDisposable());
      GrTypeDefinition clazz = file.getTypeDefinitions()[0];
      PsiTypeParameter typeParameter = clazz.getTypeParameters()[0];
      assertEquals(1, typeParameter.getExtendsList().getReferencedTypes().length);
    });
  }

  @Test
  public void test_PCE_during_retrieving_stub_index_of_an_AST_based_PSI_does_not_break_everything_later() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Random random = new Random();
    final GroovyFileImpl file = (GroovyFileImpl) myFixture.addFileToProject(
      "a.groovy",
      StringGroovyMethods.multiply("@Anno int var; ", 3)
    );

    EdtTestUtil.runInEdtAndWait(() -> {
      for (int i = 0; i < 5; i++) {
        int cancelAt = random.nextInt(100);

        assertNotNull(file.getNode());
        final List<GrVariableDeclaration> decls = ContainerUtil.reverse(
          SyntaxTraverser.psiTraverser(file).filter(GrVariableDeclaration.class).toList()
        );

        new BombedProgressIndicator(cancelAt)
          .runBombed(() -> assertInstanceOf(PsiAnchor.StubIndexReference.class, PsiAnchor.create(decls.get(0))));

        GCUtil.tryGcSoftlyReachableObjects();

        try {
          WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
            file.getViewProvider().getDocument().insertString(0, " ");
            PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
          });

          for (GrVariableDeclaration decl : decls) {
            assertNotNull(decl.getNode());
            assertTrue(decl.isValid());
          }
        }
        catch (Throwable e) {
          throw new RuntimeException("Failed with cancelAt=" + cancelAt, e);
        }
      }
    });
  }

  @Test
  public void test_no_SOE_when_AST_spine_building_queries_file_stub() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final GroovyFileImpl file =
      DefaultGroovyMethods.asType(myFixture.addFileToProject("a.groovy", "@Anno int var"), GroovyFileImpl.class);

    EdtTestUtil.runInEdtAndWait(() -> {
      assertNotNull(file.getNode());
      GrVariableDeclaration decl = SyntaxTraverser.psiTraverser(file).filter(GrVariableDeclaration.class).first();
      assertNotNull(decl);

      for (int i = 0; i < 2; i++) {
        assertInstanceOf(PsiAnchor.StubIndexReference.class, PsiAnchor.create(decl));

        GCWatcher.tracking(file.getNode()).ensureCollected();

        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
          file.getViewProvider().getDocument().insertString(0, " ");
          PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
        });
        assertNotNull(decl.getNode());
        assertTrue(decl.isValid());
      }
    });
  }
}