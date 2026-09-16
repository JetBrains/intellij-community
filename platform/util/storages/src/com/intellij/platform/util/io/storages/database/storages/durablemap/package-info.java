// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.


/// A persistent Map[K,V] implemented on top of [com.intellij.platform.util.io.storages.database.spi.BlocksDatabase]
///
/// `(key, value)` pairs are stored in `block[tag=DATA]`, with append-only-log structure in each block
/// (see [com.intellij.platform.util.io.storages.database.storages.appendonlylog.AppendOnlyLogOverBlock])
///
/// Lookup index `key.hash(int32) -> recordRef(int64)` is stored as `block[tag=LOOKUP]`, specific format
/// is pluggable: currently [com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapInt32ToInt64]
/// is used, btree impl is expected too.
///
@Internal
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import org.jetbrains.annotations.ApiStatus.Internal;
