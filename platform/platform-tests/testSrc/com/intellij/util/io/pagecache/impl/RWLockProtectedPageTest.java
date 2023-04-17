// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RWLockProtectedPageTest extends PageImplTestBase {
  @Override
  protected PageImpl createPage(int pageIndex,
                                int pageSize,
                                PageToStorageHandle pageToStorageHandle) {
    return new RWLockProtectedPageImpl(pageIndex, pageSize, pageToStorageHandle, new ReentrantReadWriteLock());
  }
}
