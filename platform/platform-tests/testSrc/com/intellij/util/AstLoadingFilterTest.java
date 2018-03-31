// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.exceptionCases.AbstractExceptionCase;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

public class AstLoadingFilterTest extends LightPlatformCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("ast.loading.filter").setValue(true, getTestRootDisposable());
  }

  private PsiFileImpl addFile() {
    return (PsiFileImpl)myFixture.addFileToProject(
      "classes.java",
      "class A {\n" +
      "  void foo() {}\n" +
      "}\n" +
      "class B {}\n"
    );
  }

  public void testDisabledLoading() throws Throwable {
    PsiFileImpl file = addFile();
    assertFalse(file.isContentsLoaded());
    assertException(new AbstractExceptionCase() {
      @Override
      public Class getExpectedExceptionClass() {
        return AssertionError.class;
      }

      @Override
      public void tryClosure() {
        AstLoadingFilter.disableTreeLoading(() -> file.getNode());
      }
    });
  }

  public void testForceEnableLoading() throws Throwable {
    PsiFileImpl file = addFile();
    assertFalse(file.isContentsLoaded());
    assertNoException(new AbstractExceptionCase<Throwable>() {
      @Override
      public Class<Throwable> getExpectedExceptionClass() {
        return Throwable.class;
      }

      @Override
      public void tryClosure() throws Throwable {
        AstLoadingFilter.disableTreeLoading(() -> AstLoadingFilter.forceEnableTreeLoading(() -> file.getNode()));
      }
    });
  }

  public void testForceEnableLoadingBeforeDisabling() throws Throwable {
    PsiFileImpl file = addFile();
    assertFalse(file.isContentsLoaded());
    assertException(new AbstractExceptionCase() {
      @Override
      public Class<?> getExpectedExceptionClass() {
        return IllegalStateException.class;
      }

      @Override
      public void tryClosure() throws Throwable {
        AstLoadingFilter.forceEnableTreeLoading(() -> file.getNode());
      }
    });
  }
}
