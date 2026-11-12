// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Iterator;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GrAliasedImportInheritorsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void test_aliased_import() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass iface = myFixture.addClass("""
                                          package pckg;
                                          public interface MyInterface {}
                                          """);

    myFixture.addFileToProject("a.groovy", """
      import pckg.MyInterface as Roo
      
      class MyClass implements Roo {}
      enum MyEnum implements Roo {}
      trait MyTrait implements Roo {}
      new Roo() {}
      """);

    Collection<PsiClass> inheritors = ReadAction.computeBlocking(() -> DirectClassInheritorsSearch.search(iface).findAll());
    assertEquals(4, inheritors.size());
  }

  @Test
  public void test_aliased_import_with_generics() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass iface = myFixture.addClass("""
                                                package pckg;
                                                public interface MyInterface<T> {}
                                                """);

    myFixture.addFileToProject("a.groovy", """
      import pckg.MyInterface as Roo
      
      class MyClass implements Roo<
      String> {}
      enum MyEnum implements Roo<? extends Integer> {}
      trait MyTrait implements Roo<Long
      > {}
      new Roo<Double>() {}
      """);

    Collection<PsiClass> inheritors = ReadAction.computeBlocking(() -> DirectClassInheritorsSearch.search(iface).findAll());
    assertEquals(4, inheritors.size());
    for (PsiClass inheritor : inheritors) {
      ReadAction.runBlocking(() -> {
        PsiClassType type = ContainerUtil.find(((GrTypeDefinition)inheritor).getSuperTypes(false), t -> t.resolve() == iface);
        assertNotNull(type);
        PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
        assertEquals(iface, resolveResult.getElement());
        assertNotNull(resolveResult.getSubstitutor().substitute(iface.getTypeParameters()[0]));
      });
    }
  }

  @Test
  public void test_aliased_import_fqn() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package foo;
                         public interface Foo{}
                         """);

    PsiClass iface = myFixture.addClass("""
                                          package bar;
                                          public interface Bar{}
                                          """);

    myFixture.addFileToProject("a.groovy", """
      package test
      
      import foo.Foo as Bar
      
      new bar.Bar(){}
      """);

    Collection<PsiClass> inheritors = ReadAction.computeBlocking(() -> DirectClassInheritorsSearch.search(iface).findAll());
    assertEquals(1, inheritors.size());

    Iterator<PsiClass> iterator = inheritors.iterator();
    assertTrue(iterator.hasNext());
    PsiClass clazz = iterator.next();
    assertNotNull(clazz);
    assertTrue(clazz instanceof GrAnonymousClassDefinition);
  }

  @Test
  public void test_aliased_import_redefined_in_same_package() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass iface = myFixture.addClass("""
                                          package foo;
                                          public interface Foo {}
                                          """);

    myFixture.addClass("""
                         package test;
                         public class Bar {}
                         """);

    myFixture.addFileToProject("test/a.groovy", """
      package test
      
      import foo.Foo as Bar
      
      new Bar() {} // inherits foo.Foo
      """);

    Collection<PsiClass> inheritors = ReadAction.computeBlocking(() -> DirectClassInheritorsSearch.search(iface).findAll());
    assertEquals(1, inheritors.size());
    Iterator<PsiClass> iterator = inheritors.iterator();
    assertTrue(iterator.hasNext());
    assertNotNull(iterator.next());
  }

  @Test
  public void test_aliased_import_redefined_in_same_file() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass iface = myFixture.addClass("""
                                          package foo;
                                          public interface Foo {}
                                          """);

    myFixture.addFileToProject("test/a.groovy", """
      package test
      
      import foo.Foo as Bar
      
      class Bar {}
      
      new Bar() {} // inherits foo.Foo
      """);

    Collection<PsiClass> inheritors = ReadAction.computeBlocking(() -> DirectClassInheritorsSearch.search(iface).findAll());
    assertEquals(1, inheritors.size());
    Iterator<PsiClass> iterator = inheritors.iterator();
    assertTrue(iterator.hasNext());
    assertNotNull(iterator.next());
  }
}
