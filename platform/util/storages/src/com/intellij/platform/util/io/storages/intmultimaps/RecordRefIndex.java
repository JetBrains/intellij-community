// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps;

import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;

/// Record index `(key.hash -> RecordRef)` (=[IntToMultiLongMap]), with 2 additional responsibilities:
/// - lifecycle operations required by a durable map ([Durable])
/// - recoverability support: [markDirty] and [closeKeepingDirty]
///
/// Recoverability model: the Index is not expected to be crash-tolerant by itself -- most index implementations are not suited
///  for it. So instead it maintains 'vulnerability range': `['mark dirty'..'close']` -- if an app crashes in this period, Index
///  assumed to be corrupted (=unreliable), and must be fully rebuilt based on source data.
///  Vulnerability range is maintained like this:
///  - Normally, Index is opened in `!dirty` state;
///  - _Any_ modification marks it 'dirty'
///  - _Successful_ [close] marks index `!dirty` before the actual closing
///  - Explicit [markDirty] and [closeKeepingDirty] exist for more fine-grained control, see theirs docs for details
@ApiStatus.Internal
public interface RecordRefIndex extends IntToMultiLongMap, Durable {
  /// Explicitly marks the index `dirty`.
  ///
  /// Any modification makes Index `dirty` automatically, but this is not enough: if golden-source data is modified,
  ///  and an app crashes _before_ these modifications are also applied to the Index -- the index could be `!dirty`
  ///  yet, but it does fall out of sync, because the golden-source has the changes Index hasn't.
  /// This method should be used to close this gap: it should be called _before_ any golden-source modification, so
  ///  Index is already marked as `dirty` (read: 'potentially out of sync'), and if the crash happens -- Index could
  ///  be detected as 'probably out of sync, and needs rebuilding'.
  void markDirty() throws IOException;

  /// Closes the index without making it `!dirty`;
  ///
  /// Regular [close] marks Index as `!dirty` before flushing and closing -- this method, in contrast, should be used
  ///  then Index is closed after a failure, so its content can't be trusted.
  ///  So it is 'just release resources, the data is unreliable anyway' close, while regular [close] is 'store reliable
  ///  data _and_ release resources'
  void closeKeepingDirty() throws IOException;
}
