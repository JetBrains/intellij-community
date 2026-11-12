// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class LiteralConstructorUsagesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testList_AsCast() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass foo = myFixture.addClass("class Foo { Foo() {} }");
    myFixture.addFileToProject("a.groovy", "def x = [] as Foo");
    assertThat(ReadAction.computeBlocking(()->ReferencesSearch.search(foo.getConstructors()[0]).findAll())).hasSize(1);
  }

  @Test
  public void testMap_AsCast() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass foo = myFixture.addClass("class Foo { Foo() {} }");
    myFixture.addFileToProject("a.groovy", "def x = [:] as Foo");
    assertThat(ReadAction.computeBlocking(()->ReferencesSearch.search(foo.getConstructors()[0]).findAll())).hasSize(1);
  }

  @Test
  public void testLiteralConstructorWithNamedArgs() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("a.groovy", """
      import groovy.transform.Immutable

      @Immutable class Money {
          String currency
          int amount
      }

      Money d = [amount: 100, currency:'USA']
      """);
    ReadAction.runBlocking(() -> {
      PsiMethod[] constructors = myFixture.findClass("Money").getConstructors();
      assertThat(ReadAction.computeBlocking(()->MethodReferencesSearch.search(constructors[0]).findAll())).isNullOrEmpty();
      assertThat(ReadAction.computeBlocking(()->MethodReferencesSearch.search(constructors[1]).findAll())).hasSize(1);
    });
  }
}
