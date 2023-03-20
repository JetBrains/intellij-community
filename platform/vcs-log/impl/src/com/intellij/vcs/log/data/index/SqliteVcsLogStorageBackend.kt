// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "SqlResolve")

package com.intellij.vcs.log.data.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.openapi.util.io.NioFiles
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
import org.intellij.lang.annotations.Language
import org.jetbrains.sqlite.*
import java.nio.file.Files
import java.util.function.IntConsumer
import java.util.function.IntFunction
import java.util.function.ToIntFunction

private const val DB_VERSION = 2

@Language("SQLite")
private const val TABLE_SCHEMA = """
  begin transaction;
  
  create table log (
    commitId integer primary key,
    message text not null,
    authorTime integer not null,
    commitTime integer not null,
    committerId integer null
  ) strict;
  create virtual table fts_message_index using fts5(message, content='log', content_rowid='commitId', tokenize='trigram');
  
  create trigger log_ai after insert on log begin
    insert into fts_message_index(rowid, message) values (new.commitId, new.message);
  end;
  create trigger log_ad after delete on log begin 
    insert into fts_message_index(fts_message_index, rowid, message) values ('delete', old.commitId, old.message);
  end;
  create trigger log_au after update on log begin
    insert into fts_message_index(fts_message_index, rowid, message) values ('delete', old.commitId, old.message);
    insert into fts_message_index(rowid, message) values (new.commitId, new.message);
  end;
  
  -- one to many relation, so, commitId is not a primary key
  create table parent (commitId integer not null, parent integer not null) strict;
  create index parent_index on parent (commitId);
  
  create table rename (parent integer not null, child integer not null, rename integer not null) strict;
  create index rename_index on rename (parent, child);
  
  commit transaction;
"""

internal const val SQLITE_VCS_LOG_DB_FILENAME_PREFIX = "vcs-log-v"

@Service(Service.Level.PROJECT)
private class ProjectLevelStoreManager(project: Project) : Disposable {
  var connection: SqliteConnection
    private set

  private val dbFile = project.getProjectDataPath(
    "$SQLITE_VCS_LOG_DB_FILENAME_PREFIX${DB_VERSION}-${VcsLogStorageImpl.VERSION}-${VcsLogPersistentIndex.VERSION}.db")

  @Suppress("DEPRECATION")
  private val coroutineScope: CoroutineScope = ApplicationManager.getApplication().coroutineScope.childScope()

  @Volatile
  var isFresh = false

  init {
    connection = connect()
  }

  private fun connect(): SqliteConnection {
    isFresh = !Files.exists(dbFile)
    val connection = SqliteConnection(dbFile)
    if (isFresh) {
      connection.execute(TABLE_SCHEMA)
    }
    return connection
  }

  fun recreate() {
    connection.close()
    // not a regular Files.deleteIfExists; to use a repeated delete operation to overcome possible issues on Windows
    NioFiles.deleteRecursively(dbFile)
    connection = connect()
  }

  override fun dispose() {
    try {
      connection.close()
    }
    finally {
      coroutineScope.cancel()
    }
  }
}

private const val RENAME_SQL = "insert into rename(parent, child, rename) values(?, ?, ?)"
private const val RENAME_DELETE_SQL = "delete from rename where parent = ? and child = ?"

internal class SqliteVcsLogStorageBackend(project: Project) : VcsLogStorageBackend {
  private val connectionManager = project.service<ProjectLevelStoreManager>()

  override var isFresh: Boolean
    get() = connectionManager.isFresh
    set(value) {
      connectionManager.isFresh = value
    }

  private val connection: SqliteConnection
    get() = connectionManager.connection

  override fun createWriter(): VcsLogWriter = SqliteVcsLogWriter(connection)

  override val isEmpty: Boolean
    get() = connection.selectBoolean("select not exists (select 1 from log)")

  override fun containsCommit(commitId: Int): Boolean {
    return connection.selectBoolean("select exists(select 1 from log where commitId = ?)", commitId)
  }

  override fun collectMissingCommits(commitIds: IntSet, missing: IntSet) {
    val batch = IntBinder(paramCount = 1)
    connection.prepareStatement("select exists (select commitId from log where commitId = ?)", batch).use { statement ->
      commitIds.forEach(IntConsumer {
        batch.bind(it)
        if (!statement.selectBoolean()) {
          missing.add(it)
        }
      })
    }
  }

  override fun getMessage(commitId: Int): String? {
    return connection.selectString("select message from log where commitId = ?", commitId)
  }

  override fun getCommitterOrAuthor(commitId: Int, getUserById: IntFunction<VcsUser>, getAuthorForCommit: IntFunction<VcsUser>): VcsUser? {
    val batch = IntBinder(paramCount = 1)
    connection.prepareStatement("select committerId from log where commitId = ?", batch).use { statement ->
      batch.bind(commitId)
      val resultSet = statement.executeQuery()
      if (!resultSet.next()) {
        return null
      }

      val result = resultSet.getInt(0)
      return if (resultSet.wasNull()) getAuthorForCommit.apply(commitId) else getUserById.apply(result)
    }
  }

  override fun getTimestamp(commitId: Int): LongArray? {
    val batch = IntBinder(paramCount = 1)
    connection.prepareStatement("select authorTime, commitTime from log where commitId = ?", batch).use { statement ->
      batch.bind(commitId)
      val resultSet = statement.executeQuery()
      if (!resultSet.next()) {
        return null
      }
      return longArrayOf(resultSet.getLong(0), resultSet.getLong(1))
    }
  }

  override fun getParent(commitId: Int): IntArray {
    val batch = IntBinder(paramCount = 1)
    connection.prepareStatement("select parent from parent where commitId = ?", batch).use { statement ->
      batch.bind(commitId)
      return readIntArray(statement)
    }
  }

  override fun processMessages(processor: (Int, String) -> Boolean) {
    connection.prepareStatement("select commitId, message from log", EmptyBinder).use { statement ->
      val resultSet = statement.executeQuery()
      while (resultSet.next()) {
        if (!processor(resultSet.getInt(0), resultSet.getString(1)!!)) {
          break
        }
      }
    }
  }

  override fun putRename(parent: Int, child: Int, renames: IntArray) {
    var success = false
    try {
      connection.beginTransaction()
      connection.execute(RENAME_DELETE_SQL, arrayOf(parent, child))

      val batch = IntBinder(paramCount = 3)
      connection.prepareStatement(RENAME_SQL, batch).use { statement ->
        for (rename in renames) {
          batch.bind(parent, child, rename)
          batch.addBatch()
        }

        statement.executeBatch()
      }

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
  }

  override fun forceRenameMap() {
  }

  override fun getRename(parent: Int, child: Int): IntArray {
    val batch = IntBinder(paramCount = 2)
    connection.prepareStatement("select rename from rename where parent = ? and child = ?", batch).use { statement ->
      batch.bind(parent, child)
      return readIntArray(statement)
    }
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

    val stringParam: String
    val batch = ObjectBinder(1)
    val statement = if (string.length >= 3) {
      stringParam = '"' + string.replace("\"", "\"\"") + '"'
      connection.prepareStatement("select rowid, message from fts_message_index(?)", batch)
    }
    else {
      stringParam = "%" + string
        .replace("!", "!!")
        .replace("%", "!%")
        .replace("_", "!_")
        .replace("[", "![") + "%"

      connection.prepareStatement("select rowid, message from fts_message_index where message like ? escape '!'", batch)
    }
    statement.use {
      batch.bind(stringParam)
      val resultSet = statement.executeQuery()
      if (!resultSet.next()) {
        // noTrigramSources is not used
        // because FTS5 falls back to a linear scan of the entire table if trigram cannot be built for the string
        return
      }

      do {
        val commitId = resultSet.getInt(0)
        if ((candidates == null || candidates.contains(commitId)) && filter.matches(resultSet.getString(1)!!)) {
          consumer.accept(commitId)
        }
      }
      while (resultSet.next())
    }
  }

  override fun markCorrupted() {
    connectionManager.recreate()
  }
}

@Suppress("SqlResolve")
private class SqliteVcsLogWriter(private val connection: SqliteConnection) : VcsLogWriter {
  init {
    connection.beginTransaction()
  }

  private val statementCollection = StatementCollection(connection)

  private val logBatch = statementCollection.prepareStatement("""
    insert into log(commitId, message, authorTime, commitTime, committerId) 
    values(?, ?, ?, ?, ?) 
    on conflict(commitId) do update set message=excluded.message
    """, ObjectBinder(paramCount = 5, batchCountHint = 256)).binder

  // first `delete`, then `insert` - `delete` statement must be added to the statement collection first
  private val parentDeleteStatement = statementCollection.prepareIntStatement("delete from parent where commitId = ?")
  private val parentStatement = statementCollection.prepareIntStatement("insert into parent(commitId, parent) values(?, ?)")

  private val renameDeleteStatement = statementCollection.prepareIntStatement(RENAME_DELETE_SQL)
  private val renameStatement = statementCollection.prepareIntStatement(RENAME_SQL)

  override fun putCommit(commitId: Int, details: VcsLogIndexer.CompressedDetails, userToId: ToIntFunction<VcsUser>) {
    val committer = if (details.author == details.committer) null else userToId.applyAsInt(details.committer)
    logBatch.bind(commitId, details.fullMessage, details.authorTime, details.commitTime, committer)
    logBatch.addBatch()
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

  override fun putRename(parent: Int, child: Int, renames: IntArray) {
    renameDeleteStatement.setInt(1, parent)
    renameDeleteStatement.setInt(2, child)
    renameDeleteStatement.addBatch()

    for (rename in renames) {
      renameStatement.setInt(1, parent)
      renameStatement.setInt(2, child)
      renameStatement.setInt(3, rename)
      renameStatement.addBatch()
    }
  }

  override fun flush() {
    statementCollection.executeBatch()
    connection.commit()
    connection.beginTransaction()
  }

  override fun close(performCommit: Boolean) {
    try {
      statementCollection.close(performCommit = performCommit)
    }
    finally {
      if (performCommit) {
        connection.commit()
      }
      else {
        connection.rollback()
      }
    }
  }
}

private fun readIntArray(statement: SqlitePreparedStatement<IntBinder>): IntArray {
  val resultSet = statement.executeQuery()
  if (!resultSet.next()) {
    // not a null because we cannot distinguish "no rows at all" vs "empty array was a value"
    return ArrayUtilRt.EMPTY_INT_ARRAY
  }

  val first = resultSet.getInt(0)
  if (!resultSet.next()) {
    return intArrayOf(first)
  }

  val result = IntArrayList()
  result.add(first)
  do {
    result.add(resultSet.getInt(0))
  }
  while (resultSet.next())
  return result.toIntArray()
}