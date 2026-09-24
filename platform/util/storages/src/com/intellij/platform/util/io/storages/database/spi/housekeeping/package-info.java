// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.


/// There are 2 kinds of housekeepers in the database:
/// - [Housekeeper]: regular housekeeper that runs in parallel with normal database operations;
/// - [OnStartupHousekeeper]: a housekeeper that runs once per DB session, on startup, **before** database
///   becomes available to the clients -- so the housekeeper has exclusive access to database internals, and
///   could do maintenance regular housekeeper can't.
///
/// Housekeeping is a **joint task** of database and application storages. DB-level housekeepers manage the chunks:
///  e.g. retire chunks with only retired blocks in them, evacuate remaining blocks from the 'sparse' chunks with
///  very few alive blocks remaining, drop the retired chunks -- these are the tasks blocks database could do regardless
///  of application storages implemented on top of it.
/// Application-level housekeepers should keep an eye on blocks allocated by the application storage, and retire the
///  blocks that are not needed anymore. Database is unaware of that 'in-storage block lifespan', so DB can't implement
///  that part of the housekeeping -- it is the responsibility of specific application-storage implementation to implement
///  and register an appropriate housekeeper.
@Internal
package com.intellij.platform.util.io.storages.database.spi.housekeeping;

import org.jetbrains.annotations.ApiStatus.Internal;
