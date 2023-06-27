// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.sqlite

class SQLiteConfig {
  // SQLite defaults to 0, but as https://github.com/xerial/sqlite-jdbc we use 3000
  private var busyTimeout: Int = 3000

  // SQLite default to DELETE (https://www.sqlite.org/pragma.html#pragma_journal_mode),
  // but WAL (https://www.sqlite.org/wal.html) "is significantly faster in most scenarios"
  private var journalMode: JournalMode = JournalMode.WAL

  // SQLite defaults to FULL (https://www.sqlite.org/pragma.html#pragma_synchronous),
  // but "FULL synchronous is very safe, but it is also slower", so
  // as we use WAL (see above) and WAL "is safe from corruption with synchronous=NORMAL", we use NORMAL as default.
  //private var synchronous: SynchronousMode = SynchronousMode.NORMAL

  // SQLite is compiled with SQLITE_DEFAULT_WAL_SYNCHRONOUS=1

  internal fun apply(db: NativeDB) {
    @Suppress("RemoveExplicitTypeArguments")
    val sql = sequence<String> {
      yield("PRAGMA busy_timeout = $busyTimeout")
      if (journalMode != JournalMode.DELETE) {
        yield("PRAGMA journal_mode = $journalMode")
      }
      //if (synchronous != SynchronousMode.FULL && journalMode != synchronous) {
      //  yield("PRAGMA synchronous = $synchronous")
      //}

      // https://www.sqlite.org/pragma.html#pragma_temp_store
      yield("PRAGMA temp_store = MEMORY")
      yield("pragma cache_size = 2000")
    }.joinToString(";")
    db.exec(sql.encodeToByteArray())
  }
}

@Suppress("unused")
private enum class JournalMode {
  DELETE,
  TRUNCATE,
  PERSIST,
  MEMORY,
  WAL,
  OFF;
}

/**
 * [Database file open modes of SQLite](https://www.sqlite.org/c3ref/open.html)
 */
@Suppress("unused", "SpellCheckingInspection")
internal enum class SQLiteOpenMode(@JvmField val flag: Int) {
  READONLY(0x00000001),

  READWRITE(0x00000002),
  CREATE(0x00000004),
  DELETEONCLOSE(0x00000008),
  OPEN_MEMORY(0x00000080),
  MAIN_DB(0x00000100),
  FULLMUTEX(0x00010000),
  SHAREDCACHE(0x00020000),
  PRIVATECACHE(0x00040000)
}
