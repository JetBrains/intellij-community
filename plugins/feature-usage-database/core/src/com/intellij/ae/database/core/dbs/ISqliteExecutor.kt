// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.core.dbs

import org.jetbrains.sqlite.SqliteConnection

interface ISqliteExecutor {
  suspend fun <T> execute(action: suspend (initDb: SqliteConnection) -> T): T?
}

interface ISqliteInternalExecutor {
  suspend fun <T> execute(action: suspend (initDb: SqliteConnection, metadata: SqliteDatabaseMetadata) -> T): T?
}