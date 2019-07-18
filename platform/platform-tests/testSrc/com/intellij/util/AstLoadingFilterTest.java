// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.ThrowableComputable;
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

  private PsiFileImpl addAnotherFile() {
    return (PsiFileImpl)myFixture.addFileToProject(
      "classes2.java",
      "class C {\n" +
      "  void foo() {}\n" +
      "}\n" +
      "class D {}\n"
    );
  }

  private static class AstLoadingExceptionCase extends AbstractExceptionCase<AstLoadingException> {

    private final Runnable myRunnable;

    private AstLoadingExceptionCase(Runnable runnable) {
      myRunnable = runnable;
    }

    @Override
    public Class<AstLoadingException> getExpectedExceptionClass() {
      return AstLoadingException.class;
    }

    @Override
    public void tryClosure() throws AssertionError {
      myRunnable.run();
    }
  }

  public void testDisallowedLoading() {
    PsiFileImpl file = addFile();
    assertFalse(file.isContentsLoaded());
    assertException(new AstLoadingExceptionCase(
      () -> AstLoadingFilter.disallowTreeLoading(
        () -> file.getNode()
      )
    ));
  }

  public void testForceAllowLoading() throws AstLoadingException {
    PsiFileImpl file = addFile();
    assertFalse(file.isContentsLoaded());
    PsiFileImpl anotherFile = addAnotherFile();
    assertFalse(anotherFile.isContentsLoaded());
    assertNoException(new AstLoadingExceptionCase(
      () -> AstLoadingFilter.disallowTreeLoading(
        () -> AstLoadingFilter.forceAllowTreeLoading(
          file,                                                                 // allow for file
          (ThrowableComputable<?, RuntimeException>)() -> file.getNode()        // access its node -> no exception
        )
      )
    ));
    assertException(new AstLoadingExceptionCase(
      () -> AstLoadingFilter.disallowTreeLoading(
        () -> AstLoadingFilter.forceAllowTreeLoading(
          file,                                                                 // allow for file
          (ThrowableComputable<?, RuntimeException>)() -> anotherFile.getNode() // access another file node -> exception
        )
      )
    ));
  }
}
