// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * TODO RC: Basically I want _all_ classes around file-page caching to be
 * moved in dedicated package -- i.e. here. E.g.:
 * {@link com.intellij.util.io.FilePageCacheLockFree},
 * {@link com.intellij.util.io.PagedFileStorageLockFree}
 * {@link com.intellij.util.io.OpenChannelsCache}
 * {@link com.intellij.util.io.DirectByteBufferAllocator}
 * Right now mostly the interoperability with 'legacy' implementations prevents it
 */
package com.intellij.util.io.pagecache;