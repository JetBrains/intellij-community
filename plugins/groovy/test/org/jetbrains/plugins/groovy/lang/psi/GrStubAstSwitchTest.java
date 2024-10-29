// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.testFramework.BombedProgressIndicator;
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ref.GCWatcher;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrAnonymousClassDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnonymousClassIndex;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class GrStubAstSwitchTest extends LightGroovyTestCase {
  public void testDontLoadContentWhenProcessingImports() {
    GroovyFileImpl file = (GroovyFileImpl)myFixture.addFileToProject("A.groovy", """
      import java.util.concurrent.ConcurrentHashMap
      
      class MyMap extends ConcurrentHashMap {}
      class B extends ConcurrentHashMap {
        void foo() {
          print 4
        }
      }
      """);
    assertFalse(file.isContentsLoaded());
    PsiClass bClass = file.getClasses()[1];
    assertFalse(file.isContentsLoaded());

    PsiMethod fooMethod = bClass.getMethods()[0];
    assertFalse(file.isContentsLoaded());

    fooMethod.findDeepestSuperMethods();
    assertFalse(file.isContentsLoaded());
  }

  public void testDontLoadAstForAnnotation() {
    GroovyFileImpl file = DefaultGroovyMethods.asType(myFixture.addFileToProject("a.groovy", """
      class A {
        def foo(){}
      }
      
      class B {
        @Delegate
        A a = new A()
      }
      """), GroovyFileImpl.class);

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
  }

  public void testDontLoadAstForAnnotation2() {
    GroovyFileImpl file = DefaultGroovyMethods.asType(myFixture.addFileToProject("a.groovy", """
      class A {
        def foo(){}
      }
      
      class B extends A {
        @Override
        def foo() {}
      }
      """), GroovyFileImpl.class);

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
  }

  public void testDelegateExists() {
    GroovyFileImpl file = DefaultGroovyMethods.asType(myFixture.addFileToProject("a.groovy", """
      class A {
        def foo(){}
      }
      
      class B {
        @Delegate
        A a = new A()
      }
      """), GroovyFileImpl.class);

    assertFalse(file.isContentsLoaded());
    PsiClass clazzB = file.getClasses()[1];
    assertFalse(file.isContentsLoaded());
    assertTrue(ContainerUtil.exists(clazzB.getMethods(), method -> method.getName().equals("foo")));
    assertFalse(file.isContentsLoaded());
  }

  public void testDefaultValueForAnnotation() {
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

    assertFalse(file.isContentsLoaded());
    PsiClass clazz = file.getClasses()[0];
    assertFalse(file.isContentsLoaded());
    PsiMethod method = clazz.getMethods()[0];
    assertFalse(file.isContentsLoaded());
    PsiAnnotation annotation = method.getModifierList().findAnnotation("pack.Ann");
    assertFalse(file.isContentsLoaded());
    assertNotNull(annotation.findAttributeValue("foo"));
    assertFalse(file.isContentsLoaded());
  }

  public void testDefaultValueForAnnotationWithAliases() {
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

    assertFalse(file.isContentsLoaded());
    PsiClass clazz = file.getClasses()[0];
    assertFalse(file.isContentsLoaded());
    PsiMethod method = clazz.getMethods()[0];
    assertFalse(file.isContentsLoaded());
    PsiAnnotation annotation = method.getModifierList().findAnnotation("pack.Ann");
    assertFalse(file.isContentsLoaded());
    assertNotNull(annotation.findAttributeValue("foo"));
    assertFalse(file.isContentsLoaded());
  }

  public void testValueForAnnotationWithAliases() {
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

    assertFalse(file.isContentsLoaded());
    PsiClass clazz = file.getClasses()[0];
    assertFalse(file.isContentsLoaded());
    PsiMethod method = clazz.getMethods()[0];
    assertFalse(file.isContentsLoaded());
    PsiAnnotation annotation = method.getModifierList().findAnnotation("pack.Ann");
    assertFalse(file.isContentsLoaded());
    assertNotNull(annotation.findAttributeValue("foo"));
    assertTrue(file.isContentsLoaded());
  }

  public void test_do_not_load_ast_for_annotation_reference_value() {
    GroovyFileImpl file = DefaultGroovyMethods.asType(myFixture.addFileToProject("Pogo.groovy", """
      @groovy.transform.AutoClone(style=groovy.transform.AutoCloneStyle.SIMPLE)
      class Pogo {}\s
      """), GroovyFileImpl.class);
    assertFalse(file.isContentsLoaded());
    PsiClass clazz = file.getClasses()[0];
    assertFalse(file.isContentsLoaded());
    PsiMethod method = ContainerUtil.find(clazz.getMethods(), m -> m.getName().equals("cloneOrCopyMembers"));
    assertNotNull(method);
    assertFalse(file.isContentsLoaded());
    assertTrue(method.hasModifierProperty(PsiModifier.PROTECTED));
    assertFalse(file.isContentsLoaded());
  }

  public void test_do_not_load_content_for_findMethodsByName() {
    GroovyFileImpl file = DefaultGroovyMethods.asType(myFixture.addFileToProject("usage.groovy", """
      class X {
        void foo(int a, int b = 2) {}
      }
      """), GroovyFileImpl.class);
    assertFalse(file.isContentsLoaded());
    PsiClass clazz = file.getClasses()[0];
    assertFalse(file.isContentsLoaded());

    assertEquals(2, DefaultGroovyMethods.size(clazz.findMethodsByName("foo", false)));
    assertFalse(file.isContentsLoaded());
  }

  public void test_do_not_load_content_for_anonymous_class__baseClassType() throws IOException {
    VirtualFile file = myFixture.getTempDirFixture().createFile("A.groovy", """
      class A {
        def field = new Runnable() {
          void run() {}
        }
      }
      """);
    PsiFileImpl psiFile = DefaultGroovyMethods.asType(PsiManager.getInstance(getProject()).findFile(file), PsiFileImpl.class);
    assertNotNull(psiFile.getStub());
    assertFalse(psiFile.isContentsLoaded());

    final Collection<GrAnonymousClassDefinition> classes =
      StubIndex.getElements(GrAnonymousClassIndex.KEY, "Runnable", getProject(), GlobalSearchScope.allScope(getProject()),
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
  }

  public void test_do_not_load_contents_in_highlighting() throws IOException {
    final VirtualFile file = getFixture().getTempDirFixture().createFile("classes.groovy", """
      class C {
        static void staticVoidMethod(a, b = 1) {}
      }
      """);
    ((JavaCodeInsightTestFixtureImpl)getFixture()).setVirtualFileFilter(f -> f.equals(file));
    getFixture().configureByText("_.groovy", "C.staticVoidMethod(1)");
    getFixture().checkHighlighting();
  }

  public void test_do_not_load_AST_when_method_has_no_comment() throws IOException {
    VirtualFile file = getFixture().getTempDirFixture().createFile("classes.groovy", """
      class C {
        static void someMethod() {}
        /**
        *
        */
        static void someMethodWithDocs() {}
      }
      """);
    GroovyFileImpl psiFile = DefaultGroovyMethods.asType(getPsiManager().findFile(file), GroovyFileImpl.class);
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
  }

  public void test_type_parameters_bounds() {
    GroovyFileImpl file = (GroovyFileImpl) getFixture().addFileToProject("classes.groovy", "class C<T extends Runnable> {}");
    ((PsiManagerEx)getPsiManager()).setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, getTestRootDisposable());
    GrTypeDefinition clazz = file.getTypeDefinitions()[0];
    PsiTypeParameter typeParameter = clazz.getTypeParameters()[0];
    assertEquals(1, typeParameter.getExtendsList().getReferencedTypes().length);
  }

  public void test_PCE_during_retrieving_stub_index_of_an_AST_based_PSI_does_not_break_everything_later() {
    Random random = new Random();
    final GroovyFileImpl file = (GroovyFileImpl) getFixture().addFileToProject(
      "a.groovy",
      StringGroovyMethods.multiply("@Anno int var; ", 3)
    );

    for (int i = 0; i < 5; i++) {
      int cancelAt = random.nextInt(100);

      assertNotNull(file.getNode());
      final List<GrVariableDeclaration> decls = ContainerUtil.reverse(
        SyntaxTraverser.psiTraverser(file).filter(GrVariableDeclaration.class).toList()
      );

      new BombedProgressIndicator(cancelAt)
        .runBombed(() -> assertInstanceOf(PsiAnchor.create(decls.get(0)), PsiAnchor.StubIndexReference.class));

      GCUtil.tryGcSoftlyReachableObjects();

      try {
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
          file.getViewProvider().getDocument().insertString(0, " ");
          PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
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
  }

  public void test_no_SOE_when_AST_spine_building_queries_file_stub() {
    final GroovyFileImpl file =
      DefaultGroovyMethods.asType(getFixture().addFileToProject("a.groovy", "@Anno int var"), GroovyFileImpl.class);

    assertNotNull(file.getNode());
    GrVariableDeclaration decl = SyntaxTraverser.psiTraverser(file).filter(GrVariableDeclaration.class).first();
    assertNotNull(decl);

    for (int i = 0; i < 2; i++) {
      assertInstanceOf(PsiAnchor.create(decl), PsiAnchor.StubIndexReference.class);

      GCWatcher.tracking(file.getNode()).ensureCollected();

      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        file.getViewProvider().getDocument().insertString(0, " ");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      });
      assertNotNull(decl.getNode());
      assertTrue(decl.isValid());
    }
  }
}