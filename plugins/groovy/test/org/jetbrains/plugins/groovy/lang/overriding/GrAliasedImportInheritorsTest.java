// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.Collection;
import java.util.Iterator;

public class GrAliasedImportInheritorsTest extends LightGroovyTestCase {
  public void test_aliased_import() {
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

    Collection<PsiClass> inheritors = DirectClassInheritorsSearch.search(iface).findAll();
    assertEquals(4, inheritors.size());
  }

  public void test_aliased_import_with_generics() {
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

    Collection<PsiClass> inheritors = DirectClassInheritorsSearch.search(iface).findAll();
    assertEquals(4, inheritors.size());
    for (PsiClass inheritor : inheritors) {
      PsiClassType type = ContainerUtil.find(((GrTypeDefinition)inheritor).getSuperTypes(false), t -> t.resolve() == iface);
      assertNotNull(type);
      PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
      assertEquals(iface, resolveResult.getElement());
      assertNotNull(resolveResult.getSubstitutor().substitute(iface.getTypeParameters()[0]));
    }
  }

  public void test_aliased_import_fqn() {
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

    Collection<PsiClass> inheritors = DirectClassInheritorsSearch.search(iface).findAll();
    assertEquals(1, inheritors.size());

    Iterator<PsiClass> iterator = inheritors.iterator();
    assertTrue(iterator.hasNext());
    PsiClass clazz = iterator.next();
    assertNotNull(clazz);
    assertTrue(clazz instanceof GrAnonymousClassDefinition);
  }

  public void test_aliased_import_redefined_in_same_package() {
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

    Collection<PsiClass> inheritors = DirectClassInheritorsSearch.search(iface).findAll();
    assertEquals(1, inheritors.size());
    Iterator<PsiClass> iterator = inheritors.iterator();
    assertTrue(iterator.hasNext());
    assertNotNull(iterator.next());
  }

  public void test_aliased_import_redefined_in_same_file() {
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

    Collection<PsiClass> inheritors = DirectClassInheritorsSearch.search(iface).findAll();
    assertEquals(1, inheritors.size());
    Iterator<PsiClass> iterator = inheritors.iterator();
    assertTrue(iterator.hasNext());
    assertNotNull(iterator.next());
  }
}
