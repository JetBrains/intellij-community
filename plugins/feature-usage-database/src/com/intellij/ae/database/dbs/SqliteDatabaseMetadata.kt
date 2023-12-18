// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.dbs

import com.intellij.ae.database.IdService
import com.intellij.ae.database.dbs.migrations.LAST_DB_VERSION
import com.intellij.ae.database.dbs.migrations.MIGRATIONS
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.sqlite.ObjectBinderFactory
import org.jetbrains.sqlite.SqliteConnection
import org.jetbrains.sqlite.SqliteException

private val logger = logger<SqliteDatabaseMetadata>()

/**
 * A class that checks that database is properly initialized, runs migrations and stores metadata information.
 * Use it to run something in database once per application run
 *
 * If you want to add new migration, add text to [MIGRATIONS] array
 *
 * This class must be initialized on background thread
 */
class SqliteDatabaseMetadata internal constructor(private val connection: SqliteConnection, isNewFile: Boolean) {
  /**
   * ID from database that represents a pair of IDE ID [IdService.id]
   * and machine ID [IdService.machineId] and IDE family [ApplicationInfo]
   */
  val ideId: Int

  init {
    ThreadingAssertions.assertBackgroundThread()
    if (isNewFile) {
      logger.info("New database, executing migration")
      executeMigrations(0)
    }
    else {
      val version = try {
        getVersion()
      }
      catch (t: SqliteException) {
        if (t.message == "[SQLITE_ERROR] SQL error or missing database (no such table: meta)") {
          logger.info("File exists, but database seem to be uninitialized")
          0
        }
        else {
          throw t
        }
      }
      executeMigrations(version)
    }

    ideId = initIdeId()
  }

  private fun executeMigrations(fromVersion: Int) {
    val migrations = MIGRATIONS.subList(maxOf(fromVersion, 0), LAST_DB_VERSION)
    if (migrations.isEmpty()) {
      return
    }

    for (migration in migrations) {
      connection.execute(migration)
    }

    connection
      .prepareStatement("UPDATE meta SET version = (?) WHERE true;", ObjectBinderFactory.create1<Int>())
      .apply { binder.bind(LAST_DB_VERSION) }
      .executeUpdate()
  }

  private fun getVersion(): Int {
    return connection.selectInt("SELECT version FROM meta LIMIT 1") ?: -1
  }

  private fun initIdeId(): Int {
    val potentialResult = connection.prepareStatement("SELECT id FROM ide WHERE ide_id = (?) AND machine_id = (?) LIMIT 1", ObjectBinderFactory.create2<String, String>()).use { statement ->
      statement.binder.bind(IdService.getInstance().id, IdService.getInstance().machineId)
      statement.selectInt()
    }
    if (potentialResult != null) return potentialResult

    return connection.prepareStatement("insert into ide(ide_id, machine_id, family) values (?, ?, ?) RETURNING id;", ObjectBinderFactory.create3<String, String, String>()).use { statement ->
      statement.binder.bind(IdService.getInstance().id, IdService.getInstance().machineId, IdService.getInstance().ideCode)
      statement.selectInt() ?: error("Null was returned when not expected")
    }
  }
}