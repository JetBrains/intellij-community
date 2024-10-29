// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.resolve.imports.*;

import java.util.Arrays;
import java.util.List;

public class GrImportContributorTest extends LightGroovyTestCase {

  @NotNull
  private LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;

  @Override
  public @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  public void setProjectDescriptor(@NotNull LightProjectDescriptor projectDescriptor) {
    this.projectDescriptor = projectDescriptor;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().enableInspections(GrUnresolvedAccessInspection.class);
    getFixture().addClass(
      """
        package foo.bar;
        public class MyClass {
          public static Object BAR = null;
          public static void foo() {}
          public void hello() {}
        }""");
  }

  public void testRegularImport() {
    GrImportContributor.EP_NAME.getPoint().registerExtension(new GrImportContributor() {
      @Override
      public @NotNull List<GroovyImport> getFileImports(@NotNull GroovyFile file) {
        return Arrays.asList(new RegularImport("foo.bar.MyClass"));
      }
    }, myFixture.getTestRootDisposable());
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("a.groovy", "new MyClass()");
    fixture.checkHighlighting();
  }

  public void testStaticImportMethod() {
    GrImportContributor.EP_NAME.getPoint().registerExtension(new GrImportContributor() {
      @Override
      public @NotNull List<GroovyImport> getFileImports(@NotNull GroovyFile file) {
        return Arrays.asList(new StaticImport("foo.bar.MyClass", "foo"));
      }
    }, myFixture.getTestRootDisposable());
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("a.groovy", "foo()");
    fixture.checkHighlighting();
  }

  public void testStaticImportField() {
    GrImportContributor.EP_NAME.getPoint().registerExtension(new GrImportContributor() {
      @Override
      public @NotNull List<GroovyImport> getFileImports(@NotNull GroovyFile file) {
        return Arrays.asList(new StaticImport("foo.bar.MyClass", "BAR"));
      }
    }, myFixture.getTestRootDisposable());
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("a.groovy", "println BAR");
    fixture.checkHighlighting();
  }

  public void testStarImport() {
    GrImportContributor.EP_NAME.getPoint().registerExtension(new GrImportContributor() {
      @Override
      public @NotNull List<GroovyImport> getFileImports(@NotNull GroovyFile file) {
        return Arrays.asList(new StarImport("foo.bar"));
      }
    }, myFixture.getTestRootDisposable());
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("a.groovy", "new MyClass()");
    fixture.checkHighlighting();
  }

  public void testStaticStarImport() {
    GrImportContributor.EP_NAME.getPoint().registerExtension(new GrImportContributor() {
      @Override
      public @NotNull List<GroovyImport> getFileImports(@NotNull GroovyFile file) {
        return Arrays.asList(new StaticStarImport("foo.bar.MyClass"));
      }
    }, myFixture.getTestRootDisposable());
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("a.groovy", """
      println foo()
      println <warning>hello</warning>()
      println BAR""");
    fixture.checkHighlighting();
  }
}
