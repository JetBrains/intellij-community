// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/// The package provides service-provider-interface for actual storages' implementation.
/// Components, to build an actual application-level storage on top of them:
/// - [BlocksDatabase] a database of [BlocksStore.Block]s -- a low-level database on top of which application-level
///   storages are implemented;
/// - [BlocksStore] container of blocks that belong to a specific application-level storage;
@Internal
package com.intellij.platform.util.io.storages.database.spi;

import org.jetbrains.annotations.ApiStatus.Internal;
