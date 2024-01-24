// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.durablemaps;

import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleHashMap;
import com.intellij.util.io.dev.appendonlylog.ChunkedAppendOnlyLog;
import com.intellij.util.io.dev.durablemaps.AppendableDurableMap;
import com.intellij.util.io.dev.durablemaps.DurableMap;
import com.intellij.util.io.dev.enumerator.KeyDescriptorEx;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Contrary to {@link DurableMapOverAppendOnlyLog} allows to append to values (i.e., without copy-on-write).
 * Uses {@link com.intellij.util.io.dev.appendonlylog.ChunkedAppendOnlyLog} to store values as series of
 * chunks, each of chunk itself works as append-only log.
 */
//public class DurableMapWithAppendableValues<K, VItem> implements AppendableDurableMap<K, VItem> {
//
//  private final ChunkedAppendOnlyLog keyValuesLog;
//  private final ExtendibleHashMap keyHashToValueIdMap;
//
//  private final KeyDescriptorEx<K> keyDescriptor;
//  private final KeyDescriptorEx<VItem> valueDescriptor;
//
//}
