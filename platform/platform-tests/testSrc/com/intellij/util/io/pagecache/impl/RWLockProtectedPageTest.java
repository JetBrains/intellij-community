// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RWLockProtectedPageTest extends PageImplTestBase<RWLockProtectedPageImpl> {
  @Override
  protected RWLockProtectedPageImpl createPage(int pageIndex,
                                               int pageSize,
                                               @NotNull PageToStorageHandle pageToStorageHandle) {
    return new RWLockProtectedPageImpl(pageIndex, pageSize, pageToStorageHandle, new ReentrantReadWriteLock());
  }
}
