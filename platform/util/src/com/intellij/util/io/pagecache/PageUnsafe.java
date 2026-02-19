// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache;

import org.jetbrains.annotations.ApiStatus;

import java.nio.ByteBuffer;

/**
 * Part of page API that is useful for performance-critical code, but easy to use incorrectly -- hence
 * extracted into a separate interface to not bother users who don't need it.
 */
public interface PageUnsafe extends Page {

  /**
   * Direct reference to internal buffer returned. This is an 'unsafe' method to access the data.
   * It is the responsibility of the caller to ensure an appropriate read/write page lock is acquired,
   * and the page is kept 'in use' (i.e. not {@link #release()}-ed/{@link #close()}-ed) for all
   * the period of using the returned buffer.
   * <p/>
   * If caller modifies content of the returned buffer, the caller <b>must</b> inform page about
   * modifications via approriate {@link #regionModified(int, int)} call.
   * <p/>
   * Returned buffer should be used only in 'absolute positioning' way, i.e. without any access to
   * buffer.position() and buffer.limit() cursors. Use .slice()/.duplicate() if you want/need to
   * use the cursors.
   */
  ByteBuffer rawPageBuffer();

  /**
   * Must be called only under page writeLock. To be used only with writes via {@link #rawPageBuffer()}
   * as a way to inform page about a buffer region that was really modified.
   */
  void regionModified(int startOffsetModified,
                      int length);

  //=============================================================================================

  //TODO RC: IntToIntBTree uses direct access to page buffer to copy range of bytes during node
  //         resize -- on split, or regular insert. Better to have dedicated Page.copyRangeTo(Page) method
  //         for that, since now one need to acquire page locks, and they must be acquired in
  //         stable order to avoid deadlocks
  @ApiStatus.Obsolete
  ByteBuffer duplicate();

  //=============================================================================================

  /**
   * Tries to re-acquire again page what was {@link #release()}-ed/{@link #close()}-ed.<p/>
   * Released page must not be used because it could be reclaimed any moment -- but if reclamation
   * is not yet started, the page _could_ be acquired again, avoiding the cost of lookup in
   * {@link PagedStorage#pageByOffset(long, boolean)}.<p/>
   * Use of this API increases the risk of un-paired page acquire/release calls -- which results in
   * either forever acquired page (can't be reclaimed), or use-after-reclaim (NPE, or access the
   * buffer which is already re-purposed for another page of another file) -- this is why it is
   * in {@link PageUnsafe}, and this is why constant vigilance is required to use that API.<p/>
   *
   * @param acquirer who acquires the page -- for debug leaking pages purposes.
   * @return true, if page successfully acquired for use,
   * false if page can't be acquired, and must be re-queried again from {@link PagedStorage}
   */
  boolean tryAcquire(final Object acquirer);
}
