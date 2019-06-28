// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixture;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author max
 */
public class BaseFixture implements IdeaTestFixture {
  private boolean myInitialized;
  private boolean myDisposed;
  private final Disposable myTestRootDisposable = Disposer.newDisposable();

  static {
    IdeaForkJoinWorkerThreadFactory.setupPoisonFactory();
  }

  @Override
  public void setUp() throws Exception {
    Assert.assertFalse("setUp() already has been called", myInitialized);
    Assert.assertFalse("tearDown() already has been called", myDisposed);
    myInitialized = true;
  }

  @Override
  public void tearDown() throws Exception {
    if (!myInitialized) {
      return;
    }

    Assert.assertFalse("tearDown() already has been called", myDisposed);
    new RunAll(
      () -> UsefulTestCase.waitForAppLeakingThreads(10, TimeUnit.SECONDS),
      () -> disposeRootDisposable()
    ).run(ObjectUtils.notNull(mySuppressedExceptions, ContainerUtil.emptyList()));
    myDisposed = true;
    resetClassFields(getClass());
  }

  protected void disposeRootDisposable() {
    EdtTestUtil.runInEdtAndWait(() -> {
      if (!Disposer.isDisposed(myTestRootDisposable)) {
        Disposer.dispose(myTestRootDisposable);
      }
    });
  }

  private void resetClassFields(final Class<?> aClass) {
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

  @NotNull
  public final Disposable getTestRootDisposable() {
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
    List<Throwable> list = mySuppressedExceptions;
    if (list == null) {
      mySuppressedExceptions = list = new SmartList<>();
    }
    list.add(e);
  }

  private List<Throwable> mySuppressedExceptions;
}