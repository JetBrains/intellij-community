// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixture;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;

public class BaseFixture implements IdeaTestFixture {
  private boolean myInitialized;
  private boolean myDisposed;
  private @Nullable List<Throwable> mySuppressedExceptions;
  private final Disposable myTestRootDisposable = Disposer.newDisposable();

  static {
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
  }

  @Override
  public void setUp() throws Exception {
    assertFalse("setUp() already has been called", myInitialized);
    assertFalse("tearDown() already has been called", myDisposed);
    myInitialized = true;
  }

  @Override
  public void tearDown() throws Exception {
    if (!myInitialized) {
      return;
    }

    assertFalse("tearDown() already has been called", myDisposed);
    new RunAll(
      () -> {
        try {
          UsefulTestCase.waitForAppLeakingThreads(10, TimeUnit.SECONDS);
        }
        catch (AlreadyDisposedException ignore) {
        }
      },
      () -> disposeRootDisposable()
    ).run(mySuppressedExceptions);
    myDisposed = true;

    resetClassFields(getClass());
  }

  protected void disposeRootDisposable() {
    EdtTestUtil.runInEdtAndWait(() -> {
      Disposer.dispose(myTestRootDisposable);
    });
  }

  private void resetClassFields(Class<?> aClass) {
    try {
      UsefulTestCase.clearDeclaredFields(this, aClass);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    if (aClass != BaseFixture.class) {
      resetClassFields(aClass.getSuperclass());
    }
  }

  public final @NotNull Disposable getTestRootDisposable() {
    return myTestRootDisposable;
  }

  /**
   * Pass here the exception you want to be thrown first
   * E.g.<pre>
   * {@code
   *   void tearDown() {
   *     try {
   *       doTearDowns();
   *     }
   *     catch(Exception e) {
   *       addSuppressedException(e);
   *     }
   *     finally {
   *       super.tearDown();
   *     }
   *   }
   * }
   * </pre>
   */
  protected void addSuppressedException(@NotNull Throwable e) {
    if (mySuppressedExceptions == null) {
      mySuppressedExceptions = new SmartList<>();
    }
    mySuppressedExceptions.add(e);
  }
}
