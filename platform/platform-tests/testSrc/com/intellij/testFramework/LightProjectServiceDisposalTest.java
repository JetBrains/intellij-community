// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;

public class LightProjectServiceDisposalTest extends LightPlatformTestCase {

  @Service
  private static final class MyService implements Disposable {
    boolean myDisposed = false;

    @Override
    public void dispose() {
      myDisposed = true;
    }
  }

  private MyService myServiceInstance;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myServiceInstance = getProject().getService(MyService.class);
    assertFalse(myServiceInstance.myDisposed);
  }

  public void testDummy() {}

  @Override
  public void tearDown() throws Exception {
    MyService instance = myServiceInstance; // save into a variable, because the field is cleared in #clearFields
    super.tearDown();
    assertTrue(instance.myDisposed); // the actual test is here
  }
}
