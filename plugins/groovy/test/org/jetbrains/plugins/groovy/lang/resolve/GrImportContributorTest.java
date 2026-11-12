// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GrImportContributor;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImport;
import org.jetbrains.plugins.groovy.lang.resolve.imports.RegularImport;
import org.jetbrains.plugins.groovy.lang.resolve.imports.StarImport;
import org.jetbrains.plugins.groovy.lang.resolve.imports.StaticImport;
import org.jetbrains.plugins.groovy.lang.resolve.imports.StaticStarImport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GrImportContributorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    myFixture.addClass(
      """
        package foo.bar;
        public class MyClass {
          public static Object BAR = null;
          public static void foo() {}
          public void hello() {}
        }""");
  }

  @Test
  public void testRegularImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GrImportContributor.EP_NAME.getPoint().registerExtension(new GrImportContributor() {
      @Override
      public @NotNull List<GroovyImport> getFileImports(@NotNull GroovyFile file) {
        return Arrays.asList(new RegularImport("foo.bar.MyClass"));
      }
    }, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.groovy", "new MyClass()");
    myFixture.checkHighlighting();
  }

  @Test
  public void testStaticImportMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GrImportContributor.EP_NAME.getPoint().registerExtension(new GrImportContributor() {
      @Override
      public @NotNull List<GroovyImport> getFileImports(@NotNull GroovyFile file) {
        return Arrays.asList(new StaticImport("foo.bar.MyClass", "foo"));
      }
    }, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.groovy", "foo()");
    myFixture.checkHighlighting();
  }

  @Test
  public void testStaticImportField() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GrImportContributor.EP_NAME.getPoint().registerExtension(new GrImportContributor() {
      @Override
      public @NotNull List<GroovyImport> getFileImports(@NotNull GroovyFile file) {
        return Arrays.asList(new StaticImport("foo.bar.MyClass", "BAR"));
      }
    }, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.groovy", "println BAR");
    myFixture.checkHighlighting();
  }

  @Test
  public void testStarImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GrImportContributor.EP_NAME.getPoint().registerExtension(new GrImportContributor() {
      @Override
      public @NotNull List<GroovyImport> getFileImports(@NotNull GroovyFile file) {
        return Arrays.asList(new StarImport("foo.bar"));
      }
    }, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.groovy", "new MyClass()");
    myFixture.checkHighlighting();
  }

  @Test
  public void testStaticStarImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GrImportContributor.EP_NAME.getPoint().registerExtension(new GrImportContributor() {
      @Override
      public @NotNull List<GroovyImport> getFileImports(@NotNull GroovyFile file) {
        return Arrays.asList(new StaticStarImport("foo.bar.MyClass"));
      }
    }, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.groovy", """
      println foo()
      println <warning>hello</warning>()
      println BAR""");
    myFixture.checkHighlighting();
  }
}
