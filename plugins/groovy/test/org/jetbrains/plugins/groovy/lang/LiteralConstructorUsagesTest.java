// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;

public class LiteralConstructorUsagesTest extends LightGroovyTestCase {
  public void testList_AsCast() {
    PsiClass foo = myFixture.addClass("class Foo { Foo() {} }");
    myFixture.addFileToProject("a.groovy", "def x = [] as Foo");
    assertOneElement(ReferencesSearch.search(foo.getConstructors()[0]).findAll());
  }

  public void testMap_AsCast() {
    PsiClass foo = myFixture.addClass("class Foo { Foo() {} }");
    myFixture.addFileToProject("a.groovy", "def x = [:] as Foo");
    assertOneElement(ReferencesSearch.search(foo.getConstructors()[0]).findAll());
  }

  public void testLiteralConstructorWithNamedArgs() {
    myFixture.addFileToProject("a.groovy", """
      import groovy.transform.Immutable

      @Immutable class Money {
          String currency
          int amount
      }

      Money d = [amount: 100, currency:'USA']
      """);
    PsiMethod[] constructors = myFixture.findClass("Money").getConstructors();
    assertEmpty(MethodReferencesSearch.search(constructors[0]).findAll());
    assertOneElement(MethodReferencesSearch.search(constructors[1]).findAll());
  }
}
