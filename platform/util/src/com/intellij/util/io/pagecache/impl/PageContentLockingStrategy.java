// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import com.intellij.util.io.pagecache.PagedStorage;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Strategy: which lock to assign the page to protect its content
 */
@FunctionalInterface
public interface PageContentLockingStrategy {
  ReentrantReadWriteLock lockForPage(PagedStorage storage,
                                     int pageIndex);


  PageContentLockingStrategy LOCK_PER_PAGE = new PageContentLockingStrategy() {
    @Override
    public ReentrantReadWriteLock lockForPage(PagedStorage storage,
                                              int pageIndex) {
      return new ReentrantReadWriteLock();
    }

    @Override
    public String toString() {
      return "LockPerPageLockingStrategy";
    }
  };

  /** All pages share the single RWLock */
  final class SharedLockLockingStrategy implements PageContentLockingStrategy {
    private final ReentrantReadWriteLock sharedLock;

    public SharedLockLockingStrategy() {
      this(new ReentrantReadWriteLock());
    }

    public SharedLockLockingStrategy(ReentrantReadWriteLock sharedLock) {
      this.sharedLock = sharedLock;
    }

    @Override
    public ReentrantReadWriteLock lockForPage(PagedStorage storage,
                                              int pageIndex) {
      return sharedLock;
    }

    @Override
    public String toString() {
      return "SingleLockLockingStrategy[" + sharedLock + "]";
    }
  }
}
