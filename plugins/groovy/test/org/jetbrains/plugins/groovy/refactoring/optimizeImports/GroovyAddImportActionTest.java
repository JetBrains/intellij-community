// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.optimizeImports;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class GroovyAddImportActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testUseContext() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public class Log {}");
    myFixture.addClass("package bar; public class Log {}");
    myFixture.addClass("package bar; public class LogFactory { public static Log log(){} }");
    doTest("""
             public class Foo {
                 Lo<caret>g l = bar.LogFactory.log();
             }
             """, """
             import bar.Log
             
             public class Foo {
                 Lo<caret>g l = bar.LogFactory.log();
             }
             """);
  }

  public void _testReferenceWithErrors() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public class Abc<X, Y> {}");
    doTest("""
             A<caret>bc<String, > foo = null
             """, """
             import foo.Abc
             
             A<caret>bc<String, > foo = null
             """);
  }

  @Test
  public void testNewifySupport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package hello; public class Abc {}");
    doTest("""
             
             import groovy.transform.CompileStatic
             
             @Newify(pattern = /[A-Z][A-Za-z0-9_]+/)
             @CompileStatic
             void newifyImportsIncorrectlyMarkedAsUnused() {
                 def e = Ab<caret>c()
             }""", """
             
             import groovy.transform.CompileStatic
             import hello.Abc
             
             @Newify(pattern = /[A-Z][A-Za-z0-9_]+/)
             @CompileStatic
             void newifyImportsIncorrectlyMarkedAsUnused() {
                 def e = Ab<caret>c()
             }""");
  }

  @Test
  public void testNewifySupportForNestedClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package hello; public class Abc { public static class Cde {} }");
    doTest("""
             
             import groovy.transform.CompileStatic
             
             @Newify(pattern = /[A-Z][A-Za-z0-9_]+/)
             @CompileStatic
             void newifyImportsIncorrectlyMarkedAsUnused() {
                 def e = Cd<caret>e()
             }""", """
             
             import groovy.transform.CompileStatic
             import hello.Abc.Cde
             
             @Newify(pattern = /[A-Z][A-Za-z0-9_]+/)
             @CompileStatic
             void newifyImportsIncorrectlyMarkedAsUnused() {
                 def e = Cd<caret>e()
             }""");
  }

  private void doTest(String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", before);
    importClass();
    myFixture.checkResult(after);
  }

  private void importClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.launchAction(myFixture.findSingleIntention("Import class"));
  }
}
