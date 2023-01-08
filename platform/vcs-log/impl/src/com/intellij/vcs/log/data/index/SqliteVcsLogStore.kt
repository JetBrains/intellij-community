// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "SqlResolve")

package com.intellij.vcs.log.data.index

import com.intellij.mvstore.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.ui.svg.*
import com.intellij.util.ArrayUtilRt
import com.intellij.util.childScope
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogTextFilter
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.impl.VcsLogIndexer
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntSet
import kotlinx.coroutines.*
import org.h2.mvstore.*
import org.sqlite.SQLiteConfig
import org.sqlite.jdbc4.JDBC4Connection
import java.nio.file.Files
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types
import java.util.*
import java.util.function.IntConsumer
import java.util.function.IntFunction
import java.util.function.ToIntFunction

private const val DB_VERSION = 1

// don't forget to change DB_VERSION if you change database scheme
private fun createTables(connection: Connection) {
  val statement = connection.createStatement()
  statement.executeUpdate("drop table if exists data")
  //language=SQLite
  statement.executeUpdate("""
      create table if not exists log (
        commitId integer primary key,
        message text,
        authorTime integer,
        commitTime integer,
        committerId integer
      ) strict
    """)

  statement.executeUpdate("""
    create virtual table if not exists fts_message_index using fts5(message, content='log', content_rowid='commitId', tokenize='trigram')
  """.trimIndent())
  //language=SQLite
  statement.executeUpdate("""
    CREATE TRIGGER if not exists log_ai AFTER INSERT ON log BEGIN
      INSERT INTO fts_message_index(rowid, message) VALUES (new.commitId, new.message);
    END
  """.trimIndent())
  //language=SQLite
  statement.executeUpdate("""
    CREATE TRIGGER if not exists log_ad AFTER DELETE ON log BEGIN
      INSERT INTO fts_message_index(fts_message_index, rowid, message) VALUES('delete', old.commitId, old.message);
    END
  """.trimIndent())
  //language=SQLite
  statement.executeUpdate("""
    CREATE TRIGGER if not exists log_au AFTER UPDATE ON log BEGIN
      INSERT INTO fts_message_index(fts_message_index, rowid, message) VALUES('delete', old.commitId, old.message);
      INSERT INTO fts_message_index(rowid, message) VALUES (new.commitId, new.message);
    END
  """.trimIndent())

  //language=SQLite
  statement.executeUpdate("create table if not exists parent (commitId integer, parent integer) strict")
  //language=SQLite
  statement.executeUpdate("create index if not exists parent_index on parent (commitId)")

  //language=SQLite
  statement.executeUpdate("create table if not exists rename (parent integer, child integer, rename integer) strict")
  //language=SQLite
  statement.executeUpdate("create index if not exists rename_index on rename (parent, child)")

  statement.close()
}

@Service(Service.Level.PROJECT)
private class ProjectLevelStoreManager(project: Project) : Disposable {
  @JvmField
  val connection: Connection

  @Suppress("DEPRECATION")
  private val coroutineScope: CoroutineScope = ApplicationManager.getApplication().coroutineScope.childScope()

  @Volatile
  var isFresh = false

  init {
    val dbFile = project.getProjectDataPath("vcs-log-v${VcsLogStorageImpl.VERSION}-${VcsLogPersistentIndex.VERSION}-${DB_VERSION}.db")
    Files.createDirectories(dbFile.parent)

    isFresh = !Files.exists(dbFile)

    val dbPath = dbFile.toString()

    val config = SQLiteConfig()
    config.setJournalMode(SQLiteConfig.JournalMode.WAL)
    config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL)

    connection = JDBC4Connection("jdbc:sqlite:$dbPath", dbPath, config.toProperties())
    connection.autoCommit = false

    var success = false
    try {
      createTables(connection)
      success = true
    }
    finally {
      if (success) {
        connection.commit()
      }
      else {
        connection.rollback()
      }
    }

    //coroutineScope.launch(CoroutineName("auto-save VCS log")) {
    //  delay(1.minutes)
    //  store.commit()
    //}
  }

  override fun dispose() {
    try {
      connection.commit()
      connection.close()
    }
    finally {
      coroutineScope.cancel()
    }
  }
}

internal class SqliteVcsLogStore(project: Project) : VcsLogStore {
  private val connectionManager = project.service<ProjectLevelStoreManager>()

  override var isFresh: Boolean
    get() = connectionManager.isFresh
    set(value) {
      connectionManager.isFresh = value
    }

  private val connection = connectionManager.connection

  override fun createWriter(): VcsLogWriter = SqliteVcsLogWriter(connection)

  override val isEmpty: Boolean
    get() {
      connection.createStatement().use { statement ->
        //language=SQLite
        val resultSet = statement.executeQuery("select not exists (select 1 from log)")
        return resultSet.next() && resultSet.getBoolean(1)
      }
    }

  override fun containsCommit(commitId: Int): Boolean {
    //language=SQLite
    val statement = connection.prepareStatement("select exists(select 1 from log where commitId = ?)")
    statement.setInt(1, commitId)
    val resultSet = statement.executeQuery()
    val result = resultSet.next() && resultSet.getBoolean(1)
    statement.close()
    return result
  }

  override fun collectMissingCommits(commitIds: IntSet, missing: IntSet) {
    //language=SQLite
    connection.prepareStatement("select exists (select commitId from log where commitId = ?)").use { statement ->
      commitIds.forEach(IntConsumer {
        statement.setInt(1, it)
        val resultSet = statement.executeQuery()
        if (!resultSet.next() || !resultSet.getBoolean(1)) {
          missing.add(it)
        }
      })
    }
  }

  override fun getMessage(commitId: Int): String? {
    //language=SQLite
    val statement = connection.prepareStatement("select message from log where commitId = ?")
    statement.setInt(1, commitId)
    val resultSet = statement.executeQuery()
    if (!resultSet.next()) {
      return null
    }

    val result = resultSet.getString(1)
    statement.close()
    return result
  }

  override fun getCommitterOrAuthor(commitId: Int, commitToCommitter: IntFunction<VcsUser>, commitToAuthor: IntFunction<VcsUser>): VcsUser? {
    //language=SQLite
    val statement = connection.prepareStatement("select committerId from log where commitId = ?")
    statement.setInt(1, commitId)
    val resultSet = statement.executeQuery()
    if (!resultSet.next()) {
      return null
    }

    val result = resultSet.getInt(1)
    if (resultSet.wasNull()) {
      return commitToAuthor.apply(commitId)
    }

    statement.close()
    return commitToCommitter.apply(result)
  }

  override fun getTimestamp(commitId: Int): LongArray? {
    //language=SQLite
    val statement = connection.prepareStatement("select authorTime, commitTime from log where commitId = ?")
    statement.setInt(1, commitId)
    val resultSet = statement.executeQuery()
    if (!resultSet.next()) {
      return null
    }

    val result = longArrayOf(resultSet.getLong(1), resultSet.getLong(2))
    statement.close()
    return result
  }

  override fun getParent(commitId: Int): IntArray {
    //language=SQLite
    val statement = connection.prepareStatement("select parent from parent where commitId = ?")
    statement.setInt(1, commitId)
    return readIntArray(statement)
  }

  override fun processMessages(processor: (Int, String) -> Boolean) {
    //language=SQLite
    val statement = connection.prepareStatement("select commitId, message from log")
    val resultSet = statement.executeQuery()
    while (resultSet.next()) {
      if (!processor(resultSet.getInt(1), resultSet.getString(2))) {
        break
      }
    }
    statement.close()
  }

  override fun putRename(parent: Int, child: Int, renames: IntArray) {
    // clear old if any
    var statement = connection.prepareStatement("delete from rename where parent = ? and child = ?")
    statement.setInt(1, parent)
    statement.setInt(2, child)
    statement.executeUpdate()
    statement.close()

    //language=SQLite
    statement = connection.prepareStatement("insert into rename(parent, child, rename) values(?, ?, ?)")
    for (rename in renames) {
      statement.setInt(1, parent)
      statement.setInt(2, child)
      statement.setInt(3, rename)
      statement.addBatch()
    }
    statement.executeBatch()
    statement.close()
  }

  override fun forceRenameMap() {
    connection.commit()
  }

  override fun getRename(parent: Int, child: Int): IntArray {
    //language=SQLite
    val statement = connection.prepareStatement("select rename from rename where parent = ? and child = ?")
    statement.setInt(1, parent)
    statement.setInt(2, child)
    return readIntArray(statement)
  }

  override fun getCommitsForSubstring(string: String,
                                      candidates: IntSet?,
                                      noTrigramSources: MutableList<String>,
                                      consumer: IntConsumer,
                                      filter: VcsLogTextFilter) {
    // See https://www.sqlite.org/fts5.html#the_experimental_trigram_tokenizer:
    // Substrings consisting of fewer than 3 unicode characters do not match any rows when used with a full-text query.
    // If a LIKE or GLOB pattern does not contain at least one sequence of non-wildcard unicode characters,
    // FTS5 falls back to a linear scan of the entire table.
    //
    // So, we use `like` instead of a full-text query if the string is fewer than 3 chars.

    var stringParam = string
    val statement = if (string.length >= 3) {
      //language=SQLite
      connection.prepareStatement("select rowid, message from fts_message_index(?)")
    }
    else {
      stringParam = "%" + stringParam
        .replace("!", "!!")
        .replace("%", "!%")
        .replace("_", "!_")
        .replace("[", "![") + "%"

      //language=SQLite
      connection.prepareStatement("select rowid, message from fts_message_index where message like ? escape '!'")
    }
    statement.use {
      statement.setString(1, stringParam)
      val resultSet = statement.executeQuery()
      if (!resultSet.next()) {
        // noTrigramSources is not used
        // because FTS5 falls back to a linear scan of the entire table if trigram cannot be built for the string
        return
      }

      do {
        val commitId = resultSet.getInt(1)
        if ((candidates == null || candidates.contains(commitId)) && filter.matches(resultSet.getString(2))) {
          consumer.accept(commitId)
        }
      }
      while (resultSet.next())
    }
  }
}

@Suppress("SqlResolve")
private class SqliteVcsLogWriter(private val connection: Connection) : VcsLogWriter {
  //language=SQLite
  private val logStatement = connection.prepareStatement("""
    insert into log(commitId, message, authorTime, commitTime, committerId) 
    values(?, ?, ?, ?, ?) 
    on conflict(commitId) do update set message=excluded.message
    """)

  //language=SQLite
  private val parentStatement = connection.prepareStatement("insert into parent(commitId, parent) values(?, ?)")
  private val parentDeleteStatement = connection.prepareStatement("delete from parent where commitId = ?")

  override fun putCommit(commitId: Int, details: VcsLogIndexer.CompressedDetails, userToId: ToIntFunction<VcsUser>) {
    logStatement.setInt(1, commitId)
    logStatement.setString(2, details.fullMessage)
    logStatement.setLong(3, details.authorTime)
    logStatement.setLong(4, details.commitTime)

    if (details.author == details.committer) {
      logStatement.setNull(5, Types.INTEGER)
    }
    else {
      logStatement.setInt(5, userToId.applyAsInt(details.committer))
    }

    logStatement.addBatch()
  }

  override fun putParents(commitId: Int, parents: List<Hash>, hashToId: ToIntFunction<Hash>) {
    // clear old if any
    parentDeleteStatement.setInt(1, commitId)
    parentDeleteStatement.addBatch()

    for (parent in parents) {
      parentStatement.setInt(1, commitId)
      parentStatement.setInt(2, hashToId.applyAsInt(parent))
      parentStatement.addBatch()
    }
  }

  override fun flush() {
    logStatement.executeBatch()

    parentDeleteStatement.executeBatch()
    parentStatement.executeBatch()
  }

  override fun close(success: Boolean) {
    if (success) {
      flush()
      connection.commit()
    }
    else {
      connection.rollback()
    }

    parentDeleteStatement.close()
    parentStatement.close()
    logStatement.close()
  }
}

private fun readIntArray(statement: PreparedStatement): IntArray {
  val resultSet = statement.executeQuery()
  if (!resultSet.next()) {
    // not a null because we cannot distinguish "no rows at all" vs "empty array was a value"
    return ArrayUtilRt.EMPTY_INT_ARRAY
  }

  val first = resultSet.getInt(1)
  if (!resultSet.next()) {
    return intArrayOf(first)
  }

  val result = IntArrayList()
  result.add(first)
  do {
    result.add(resultSet.getInt(1))
  }
  while (resultSet.next())
  statement.close()
  return result.toIntArray()
}