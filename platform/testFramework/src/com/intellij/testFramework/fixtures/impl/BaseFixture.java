/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixture;
import junit.framework.Assert;

public class BaseFixture implements IdeaTestFixture {
  private boolean myDisposed;
  private boolean myInitialized;

  @Override
  public void setUp() throws Exception {
    Assert.assertFalse("setUp() already has been called", myInitialized);
    Assert.assertFalse("tearDown() already has been called", myDisposed);
    myInitialized = true;
  }

  @Override
  public void tearDown() throws Exception {
    Assert.assertTrue("setUp() has not been called", myInitialized);
    Assert.assertFalse("tearDown() already has been called", myDisposed);
    Disposer.dispose(myTestRootDisposable);
    myDisposed = true;
    resetClassFields(getClass());
  }

  private void resetClassFields(final Class<?> aClass) {
    try {
      UsefulTestCase.clearDeclaredFields(this, aClass);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    if (aClass == BaseFixture.class) return;
    resetClassFields(aClass.getSuperclass());
  }

  protected final Disposable myTestRootDisposable = Disposer.newDisposable();
  public Disposable getTestRootDisposable() {
    return myTestRootDisposable;
  }
  protected <T extends Disposable> T disposeOnTearDown(final T disposable) {
    Disposer.register(myTestRootDisposable, disposable);
    return disposable;
  }
}