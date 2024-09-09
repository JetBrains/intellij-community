// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.TestLoggerKt;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class AstLoadingFilterTest extends BasePlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("ast.loading.filter").setValue(true, getTestRootDisposable());
  }

  private PsiFileImpl addFile() {
    return (PsiFileImpl)myFixture.addFileToProject(
      "classes.java",
      """
        class A {
          void foo() {}
        }
        class B {}
        """
    );
  }

  private PsiFileImpl addAnotherFile() {
    return (PsiFileImpl)myFixture.addFileToProject(
      "classes2.java",
      """
        class C {
          void foo() {}
        }
        class D {}
        """
    );
  }

  public void testDisallowedLoading() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      PsiFileImpl file = addFile();
      assertFalse(file.isContentsLoaded());
      assertThrows(
        AstLoadingException.class,
        () -> AstLoadingFilter.disallowTreeLoading(
          () -> file.getNode()
        )
      );
    });
  }

  public void testForceAllowLoading() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      PsiFileImpl file = addFile();
      assertFalse(file.isContentsLoaded());
      PsiFileImpl anotherFile = addAnotherFile();
      assertFalse(anotherFile.isContentsLoaded());
      assertNoException(
        AstLoadingException.class,
        () -> AstLoadingFilter.disallowTreeLoading(
          () -> AstLoadingFilter.forceAllowTreeLoading(
            file,                                                                 // allow for file
            (ThrowableComputable<?, RuntimeException>)() -> file.getNode()        // access its node -> no exception
          )
        )
      );
      assertThrows(
        AstLoadingException.class,
        () -> AstLoadingFilter.disallowTreeLoading(
          () -> AstLoadingFilter.forceAllowTreeLoading(
            file,                                                                 // allow for file
            (ThrowableComputable<?, RuntimeException>)() -> anotherFile.getNode() // access another file node -> exception
          )
        )
      );
    });
  }
}
