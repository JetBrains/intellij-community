// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * TODO RC: Basically I want _all_ classes around file-page caching to be moved in
 * dedicated package -- i.e. here. Namely:
 * {@link com.intellij.util.io.FilePageCacheLockFree},
 * {@link com.intellij.util.io.PagedFileStorageWithRWLockedPageContent}
 * {@link com.intellij.util.io.OpenChannelsCache}
 * {@link com.intellij.util.io.DirectByteBufferAllocator}
 * <p>
 * Right now interoperability with 'legacy' implementations prevents it:
 * {@link com.intellij.util.io.OpenChannelsCache} and {@link com.intellij.util.io.DirectByteBufferAllocator}
 * are package-local, and located in {@link com.intellij.util.io}, and can't be moved
 * because legacy {@link com.intellij.util.io.FilePageCache} and {@link com.intellij.util.io.PagedFileStorage}
 * uses them -- and I dont' want to move legacy impls because a lot of classes depend
 * on them, so it is too much changes for nothing.
 */
@ApiStatus.Internal
package com.intellij.util.io.pagecache;

import org.jetbrains.annotations.ApiStatus;