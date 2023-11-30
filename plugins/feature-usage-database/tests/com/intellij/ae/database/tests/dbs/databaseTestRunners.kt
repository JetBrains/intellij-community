// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.tests.dbs

import com.intellij.ae.database.dbs.IUserActivityDatabaseLayer
import com.intellij.ae.database.dbs.SqliteLazyInitializedDatabase
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a test for [IUserActivityDatabaseLayer]
 */
fun <T : IUserActivityDatabaseLayer> runDatabaseLayerTest(
  dbFactory: (CoroutineScope) -> T,
  action: suspend (T) -> Unit,
) = runInitializedDatabaseTestInternal { cs, db -> action(dbFactory(cs)) }

fun <T : IUserActivityDatabaseLayer> runDatabaseLayerTest(
  dbFactory: (CoroutineScope) -> T,
  action: suspend (T, SqliteLazyInitializedDatabase) -> Unit,
) = runInitializedDatabaseTestInternal { cs, db -> action(dbFactory(cs), db) }

fun <T : IUserActivityDatabaseLayer> runDatabaseLayerTest(
  dbFactory: (CoroutineScope) -> T,
  action: suspend (T, SqliteLazyInitializedDatabase, CoroutineScope) -> Unit,
) = runInitializedDatabaseTestInternal { cs, db -> action(dbFactory(cs), db, cs) }

private fun runInitializedDatabaseTestInternal(action: suspend (CoroutineScope, SqliteLazyInitializedDatabase) -> Unit) {
  timeoutRunBlocking {
    withContext(Dispatchers.IO) {
      val db = SqliteLazyInitializedDatabase.getInstanceAsync()
      action(this, db)
      db.closeDatabase()
      //cancel()
    }
  }
}