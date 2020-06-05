// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.testFramework.fixtures.BareTestFixture;

public class BareTestFixtureImpl extends BaseFixture implements BareTestFixture {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    TestApplicationManager.getInstance();
  }

  @Override
  public void tearDown() throws Exception {
    // don't use method references here to make stack trace reading easier
    //noinspection Convert2MethodRef
    new RunAll(
      ()-> JarFileSystemImpl.cleanupForNextTest(),
      () -> EdtTestUtil.runInEdtAndWait(() -> HeavyPlatformTestCase.cleanupApplicationCaches(null)),
      () -> super.tearDown()
    ).run();
  }
}