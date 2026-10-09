// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/// Stores appendable records in one or more database blocks:
/// - [RecordStorageOverBlocks] manages the block sequence and selects the current block.
///   Consists of `1..N` [AppendableRecordBlocks]:
///   - [AppendableRecordsOverBlock] allocates, commits, reads, and updates records in one block.
///     - [AppendableRecordsOverBlockHeaderLayout] binary layout of a [AppendableRecordsOverBlock] header (the block cursors, version,...)
///     - [RecordHeaderLayout] binary layout of individual record header
/// - [AppendableRecordBlocks] encapsulate access to the blocks database
@ApiStatus.Internal
package com.intellij.platform.util.io.storages.database.storages.appenablerecords;

import org.jetbrains.annotations.ApiStatus;
