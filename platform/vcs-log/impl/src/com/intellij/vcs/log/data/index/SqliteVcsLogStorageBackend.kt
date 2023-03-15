// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "SqlResolve")

package com.intellij.vcs.log.data.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtilRt
import com.intellij.util.childScope
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogTextFilter
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.VcsUserRegistry
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.data.index.VcsLogPathsIndex.*
import com.intellij.vcs.log.history.EdgeData
import com.intellij.vcs.log.impl.VcsLogIndexer
import com.intellij.vcs.log.util.PersistentUtil
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.intellij.lang.annotations.Language
import org.jetbrains.sqlite.*
import java.io.IOException
import java.nio.file.Files
import java.util.function.IntConsumer
import java.util.function.IntFunction
import java.util.function.ObjIntConsumer
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
    isCommitter integer not null
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
  
  create table user (commitId integer not null, isCommitter integer not null, name text not null, email text not null) strict;
  create index user_index on user (name, email);
  
  create table path (relativePath text not null, position integer not null) strict;
  create index path_index on path (position, relativePath);
  create table path_change (commitId integer not null, pathId integer not null, kind integer not null) strict;
  create index path_change_index on path_change(pathId);
  
  commit transaction;
"""

internal const val SQLITE_VCS_LOG_DB_FILENAME_PREFIX = "vcs-log-v"

private class ProjectLevelConnectionManager(project: Project, logId: String) : Disposable {
  var connection: SqliteConnection
    private set

  private val dbFile = PersistentUtil.getPersistenceLogCacheDir(project, logId).resolve(
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

internal class SqliteVcsLogStorageBackend(project: Project,
                                          logId: String,
                                          roots: Set<VirtualFile>,
                                          disposable: Disposable) :
  VcsLogStorageBackend, VcsLogUserBiMap, VcsLogIndexedPaths, VcsLogPathsStorage {

  private val connectionManager = ProjectLevelConnectionManager(project, logId).also { Disposer.register(disposable, it) }

  private val userRegistry = project.service<VcsUserRegistry>()

  private val sortedRoots = roots.sortedWith(Comparator.comparing(VirtualFile::getPath))

  private val rootsToPosition = Object2IntOpenHashMap<VirtualFile>()
    .apply {
      sortedRoots.forEachIndexed { index, root -> put(root, index) }
    }

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
    connection.prepareStatement("select isCommitter from log where commitId = ?", batch).use { statement ->
      batch.bind(commitId)
      val resultSet = statement.executeQuery()
      if (!resultSet.next()) {
        return null
      }

      val isCommitter = resultSet.getInt(0)
      return if (isCommitter == 1) getCommitterForCommit(commitId, isCommitter) else getAuthorForCommit(commitId)
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

  override fun isUsersEmpty(): Boolean {
    return connection.selectBoolean("select not exists (select 1 from user)")
  }

  override fun getAuthorForCommit(commitId: Int): VcsUser? {
    val paramBinder = IntBinder(paramCount = 1)
    connection.prepareStatement("select name, email from user where commitId = ? and isCommitter is null",
                                paramBinder)
      .use { statement ->
        paramBinder.bind(commitId)
        val rs = statement.executeQuery()
        if (!rs.next()) {
          return null
        }

      return userRegistry.createUser(rs.getString(0)!!, rs.getString(1)!!)
    }
  }

  override fun getCommitsForUsers(users: Set<VcsUser>): IntSet {
    val commitIds = IntOpenHashSet()

    for (user in users) {
      val paramBinder = ObjectBinder(paramCount = 2)
      connection.prepareStatement("select commitId from user where name = ? and email = ?", paramBinder).use { statement ->
        paramBinder.bindMultiple(user.name, user.email)
        val rs = statement.executeQuery()
        while (rs.next()) {
          commitIds.add(rs.getInt(0))
        }
      }
    }

    return commitIds
  }

  override fun getUserId(commitId: Int, user: VcsUser): Int {
    val paramBinder = IntBinder(paramCount = 1)
    connection.prepareStatement("select rowid from user where commitId = ?", paramBinder).use { statement ->
      paramBinder.bind(commitId)
      val rs = statement.executeQuery()
      if (rs.next()) {
        return rs.getInt(0)
      }
    }

    throw IOException("User $user not found for commit $commitId")
  }

  private fun getCommitterForCommit(commitId: Int, isCommitter: Int): VcsUser? {
    val paramBinder = IntBinder(paramCount = 2)
    connection.prepareStatement("select name, email from user where commitId = ? and isCommitter = ?", paramBinder).use { statement ->
      paramBinder.bind(commitId, isCommitter)
      val rs = statement.executeQuery()
      if (rs.next()) {
        return userRegistry.createUser(rs.getString(0)!!, rs.getString(1)!!)
      }

      return null
    }
  }

  /**
   * Not used, replaced by [getCommitterForCommit]
   */
  override fun getUserById(id: Int): VcsUser? {
    throw UnsupportedOperationException()
  }

  /**
   * Not used, author/committer will be added in the same batch (see [SqliteVcsLogWriter.putCommit])
   */
  override fun update(commitId: Int, details: VcsLogIndexer.CompressedDetails) {}

  override fun flush() {}

  override fun isPathsEmpty(): Boolean {
    return connection.selectBoolean("select not exists (select 1 from path)")
  }

  override fun iterateChangesInCommits(root: VirtualFile, path: FilePath, consumer: ObjIntConsumer<List<ChangeKind>>) {
    val position = rootsToPosition.getInt(root)
    val relativePath = LightFilePath(root, path).relativePath
    val changesInCommit = Int2ObjectOpenHashMap<MutableList<ChangeKind>>()
    val paramBinder = ObjectBinder(paramCount = 2)
    connection.prepareStatement(
      "select commitId, kind from path as p join path_change as c on p.rowid = c.pathId where p.position = ? and p.relativePath = ?",
      paramBinder).use { statement ->

      paramBinder.bindMultiple(position, relativePath)

      val rs = statement.executeQuery()
      while (rs.next()) {
        val changes = changesInCommit.getOrPut(rs.getInt(0)) { arrayListOf() }
        changes.add(ChangeKind.getChangeKindById(rs.getInt(1).toByte()))
      }
    }

    for ((commitId, changes) in changesInCommit) {
      consumer.accept(changes, commitId)
    }
  }

  override fun findRename(parent: Int, child: Int, root: VirtualFile, path: FilePath, isChildPath: Boolean): EdgeData<FilePath?>? {
    val renames = getRename(parent, child)
    if (renames.isEmpty()) {
      return null
    }

    val pathId = getPathId(LightFilePath(root, path))

    for (i in renames.indices step 2) {
      val first = renames[i]
      val second = renames[i + 1]
      if ((isChildPath && second == pathId) || (!isChildPath && first == pathId)) {
        val path1 = getPath(first)?.let { toFilePath(it, path.isDirectory) }
        val path2 = getPath(second)?.let { toFilePath(it, path.isDirectory) }
        return EdgeData(path1, path2)
      }
    }

    return null
  }

  override fun getPathsEncoder(): VcsLogIndexer.PathsEncoder =
    VcsLogIndexer.PathsEncoder { root, relativePath, _ ->
      val position = rootsToPosition.getInt(root)
      val pathId = getPathId(root, relativePath)
      if (pathId != null) {
        pathId
      }
      else {
        connection.execute("insert into path(position, relativePath) values (?, ?)", arrayOf(position, relativePath))
        getPathIdOrFail(root, relativePath)
      }
    }

  override fun getPath(pathId: Int): LightFilePath? {
    val paramBinder = IntBinder(paramCount = 1)
    connection.prepareStatement("select position, relativePath from path where rowid = ?", paramBinder).use { statement ->
      paramBinder.bind(pathId)

      val rs = statement.executeQuery()
      if (rs.next()) {
        return LightFilePath(sortedRoots.get(rs.getInt(0)), rs.getString(1)!!)
      }
    }

    return null
  }

  override fun getPathId(filePath: LightFilePath): Int {
    return getPathIdOrFail(filePath.root, filePath.relativePath)
  }

  private fun getPathIdOrFail(root: VirtualFile, relativePath: String): Int {
    val pathId = getPathId(root, relativePath)
    if (pathId == null) {
      throw IOException("Path ${root} with relativePath = ${relativePath} not stored")
    }

    return pathId
  }

  private fun getPathId(root: VirtualFile, relativePath: String): Int? {
    val position = rootsToPosition.getInt(root)
    val paramBinder = ObjectBinder(paramCount = 2)
    connection.prepareStatement("select rowid from path where position = ? and relativePath = ?", paramBinder).use { statement ->
      paramBinder.bindMultiple(position, relativePath)

      val rs = statement.executeQuery()
      if (rs.next()) {
        return rs.getInt(0)
      }
    }

    return null
  }
}

@Suppress("SqlResolve")
private class SqliteVcsLogWriter(private val connection: SqliteConnection) : VcsLogWriter {
  init {
    connection.beginTransaction()
  }

  private val statementCollection = StatementCollection(connection)

  private val logBatch = statementCollection.prepareStatement("""
    insert into log(commitId, message, authorTime, commitTime, isCommitter) 
    values(?, ?, ?, ?, ?) 
    on conflict(commitId) do update set message=excluded.message
    """, ObjectBinder(paramCount = 5, batchCountHint = 256)).binder
  private val userBatch = statementCollection.prepareStatement("""
    insert into user(commitId, isCommitter, name, email) 
    values(?, ?, ?, ?) 
    """, ObjectBinder(paramCount = 4, batchCountHint = 256)).binder

  // first `delete`, then `insert` - `delete` statement must be added to the statement collection first
  private val parentDeleteStatement = statementCollection.prepareIntStatement("delete from parent where commitId = ?")
  private val parentStatement = statementCollection.prepareIntStatement("insert into parent(commitId, parent) values(?, ?)")

  private val renameDeleteStatement = statementCollection.prepareIntStatement(RENAME_DELETE_SQL)
  private val renameStatement = statementCollection.prepareIntStatement(RENAME_SQL)

  private val changeStatement = statementCollection.prepareIntStatement("insert into path_change(commitId, pathId, kind) values(?, ?, ?)")

  override fun putCommit(commitId: Int, details: VcsLogIndexer.CompressedDetails, userToId: ToIntFunction<VcsUser>) {
    val isCommitter = if (details.author == details.committer) 0 else 1
    if (isCommitter == 1) {
      userBatch.bindMultiple(commitId, isCommitter, details.committer.name, details.committer.email)
      userBatch.addBatch()
    }
    userBatch.bindMultiple(commitId, 0, details.author.name, details.author.email)
    userBatch.addBatch()
    logBatch.bind(commitId, details.fullMessage, details.authorTime, details.commitTime, isCommitter)
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

  override fun putPathChanges(commitId: Int, details: VcsLogIndexer.CompressedDetails, logStore: VcsLogStorage) {

    val changesToStore = collectChangesAndPutRenames(details, logStore)

    for (entry in changesToStore) {
      val pathId = entry.key
      val changes = entry.value

      for (change in changes) {
        changeStatement.setInt(1, commitId)
        changeStatement.setInt(2, pathId)
        changeStatement.setInt(3, change.id.toInt())
        changeStatement.addBatch()
      }
    }
  }

  private fun collectChangesAndPutRenames(details: VcsLogIndexer.CompressedDetails,
                                          logStore: VcsLogStorage): Int2ObjectOpenHashMap<List<ChangeKind>> {
    val result = Int2ObjectOpenHashMap<List<ChangeKind>>()

    // it's not exactly parents count since it is very convenient to assume that initial commit has one parent
    val parentsCount = if (details.parents.isEmpty()) 1 else details.parents.size
    for (parentIndex in 0 until parentsCount) {
      val entries = details.getRenamedPaths(parentIndex).int2IntEntrySet()

      if (entries.isNotEmpty()) {
        val renames = IntArray(entries.size * 2)
        var index = 0
        for (entry in entries) {
          renames[index++] = entry.intKey
          renames[index++] = entry.intValue
          PathIndexer.getOrCreateChangeKindList(result, entry.intKey, parentsCount)[parentIndex] = ChangeKind.REMOVED
          PathIndexer.getOrCreateChangeKindList(result, entry.intValue, parentsCount)[parentIndex] = ChangeKind.ADDED
        }
        val commit = logStore.getCommitIndex(details.id, details.root)
        val parent = logStore.getCommitIndex(details.parents[parentIndex], details.root)

        putRename(parent, commit, renames)
      }

      for (entry in details.getModifiedPaths(parentIndex).int2ObjectEntrySet()) {
        PathIndexer
          .getOrCreateChangeKindList(result, entry.intKey, parentsCount)[parentIndex] = PathIndexer.createChangeData(entry.value)
      }
    }

    return result
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
