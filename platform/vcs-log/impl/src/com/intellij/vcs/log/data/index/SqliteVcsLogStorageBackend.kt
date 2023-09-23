// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "SqlResolve")

package com.intellij.vcs.log.data.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtilRt
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.data.index.VcsLogPathsIndex.*
import com.intellij.vcs.log.history.EdgeData
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsLogErrorHandler
import com.intellij.vcs.log.impl.VcsLogIndexer
import com.intellij.vcs.log.impl.VcsRefImpl
import com.intellij.vcs.log.util.StorageId
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.intellij.lang.annotations.Language
import org.jetbrains.sqlite.*
import java.io.IOException
import java.nio.file.Files
import java.util.function.IntConsumer
import java.util.function.IntFunction
import java.util.function.ObjIntConsumer
import java.util.function.Predicate

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
  
  create table commit_hashes (hash text not null, position integer not null, name text null, type integer null) strict;
  create unique index commit_hashes_index on commit_hashes (position, hash);
  
  commit transaction;
"""

internal const val SQLITE_VCS_LOG_DB_FILENAME_PREFIX = "vcs-log-v"

private class ProjectLevelConnectionManager private constructor(@JvmField val storageId: StorageId.File) : Disposable {
  constructor(project: Project, logId: String)
    : this(StorageId.File(project.name, logId, "$SQLITE_VCS_LOG_DB_FILENAME_PREFIX$DB_VERSION-${VcsLogPersistentIndex.VERSION}", "db"))

  private val dbFile = storageId.storagePath

  @JvmField
  val connection: SqliteConnection = connect()

  @Volatile
  var isFresh = false

  @JvmField
  val selectParent = connection.statementPool(sql = "select parent from parent where commitId = ?") { IntBinder(1) }

  @JvmField
  val selectRename = connection.statementPool(sql = "select rename from rename where parent = ? and child = ?") { IntBinder(2) }

  @JvmField
  val selectPathIdPool = connection.statementPool(sql = "select rowid from path where position = ? and relativePath = ?") { ObjectBinder(2) }

  @JvmField
  val insertPathPool = connection.statementPool(sql = "insert into path(position, relativePath) values (?, ?) returning rowid") { ObjectBinder(2) }

  @JvmField
  val selectPathPool = connection.statementPool(sql = "select position, relativePath from path where rowid = ?") { IntBinder(1) }

  @JvmField
  val selectCommitterForCommitPool = connection.statementPool("select name, email from user where commitId = ? and isCommitter = ?") { IntBinder(2) }

  @JvmField
  val selectCommitsForUserPool = connection.statementPool("select commitId from user where isCommitter = 0 and name = ? and email = ?") { ObjectBinder(2) }

  @JvmField
  val insertCommitPool = connection.statementPool("insert into commit_hashes(position, hash) values(?, ?) returning rowid") { ObjectBinder(2) }
  val selectCommitPool = connection.statementPool("select rowid from commit_hashes where position = ? and hash = ?") { ObjectBinder(2) }
  val insertMoreCommitPool = connection.statementPool("insert into commit_hashes(position, hash, name, type) values(?, ?, ?, ?) returning rowid") { ObjectBinder(4) }
  val updateCommitPool = connection.statementPool("update commit_hashes set name = ?, type = ? where position = ? and hash = ? returning rowid") { ObjectBinder(4) }

  fun <R : Any> runUnderReadonlyConnection(runnable: (SqliteConnection) -> R?): R? {
    if (!Files.exists(dbFile)) {
      return null
    }
    return SqliteConnection(dbFile, readOnly = true).use { connection -> runnable(connection) }
  }

  private fun connect(): SqliteConnection {
    isFresh = !Files.exists(dbFile)
    val connection = SqliteConnection(dbFile)
    if (isFresh) {
      connection.execute(TABLE_SCHEMA)
    }
    return connection
  }

  fun recreate(): ProjectLevelConnectionManager {
    connection.close()
    // not a regular Files.deleteIfExists; to use a repeated delete operation to overcome possible issues on Windows
    NioFiles.deleteRecursively(dbFile)
    return ProjectLevelConnectionManager(storageId)
  }

  override fun dispose() = connection.interruptAndClose()
}

internal class SqliteVcsLogStorageBackend(project: Project,
                                          logId: String,
                                          private val logProviders: Map<VirtualFile, VcsLogProvider>,
                                          private val errorHandler: VcsLogErrorHandler,
                                          private val disposable: Disposable) : VcsLogStorageBackend, VcsLogStorage {
  @Volatile
  private var connectionManager = ProjectLevelConnectionManager(project, logId).also {
    Disposer.register(disposable, it)
  }

  private val userRegistry = project.service<VcsUserRegistry>()

  private val sortedRoots = logProviders.keys.sortedWith(Comparator.comparing(VirtualFile::getPath))

  private val rootsToPosition = Object2IntOpenHashMap<VirtualFile>().apply {
    sortedRoots.forEachIndexed { index, root -> put(root, index) }
  }

  override val storageId: StorageId.File
    get() = connectionManager.storageId

  override var isFresh: Boolean
    get() = connectionManager.isFresh
    set(value) {
      connectionManager.isFresh = value
    }

  override val isEmpty: Boolean get() = connection.selectBoolean("select not exists (select 1 from log)")

  private val connection: SqliteConnection
    get() = connectionManager.connection

  override fun createWriter(): VcsLogWriter = SqliteVcsLogWriter(connection, this)

  override fun containsCommit(commitId: Int): Boolean {
    return connection.selectBoolean("select exists(select 1 from log where commitId = ?)", commitId)
  }

  override fun collectMissingCommits(commitIds: IntSet): IntSet {
    val missing = IntOpenHashSet()
    val batch = IntBinder(paramCount = 1)
    connection.prepareStatement("select exists (select commitId from log where commitId = ?)", batch).use { statement ->
      commitIds.forEach(IntConsumer {
        batch.bind(it)
        if (!statement.selectBoolean()) {
          missing.add(it)
        }
      })
    }
    return missing
  }

  override fun iterateIndexedCommits(limit: Int, processor: IntFunction<Boolean>) {
    connectionManager.runUnderReadonlyConnection { connection ->
      val limitClause = if (limit > 0) " limit $limit" else ""
      connection.prepareStatement("select commitId from log$limitClause", IntBinder(paramCount = 0)).use { statement ->
        val rs = statement.executeQuery()
        while (rs.next()) {
          if (!processor.apply(rs.getInt(0))) return@runUnderReadonlyConnection
        }
      }
    }
  }

  override fun getMessage(commitId: Int): String? {
    return connection.selectString("select message from log where commitId = ?", commitId)
  }

  override fun getMessages(commitIds: Collection<Int>): Map<Int, String> {
    val result = hashMapOf<Int, String>()
    val paramBinder = ObjectBinder(paramCount = 0)
    val inClause = commitIds.toInClause()
    val sql = "select commitId, message from log where commitId in $inClause"

    connection.prepareStatement(sql, paramBinder).use { statement ->
      val rs = statement.executeQuery()
      while (rs.next()) {
        val commitId = rs.getInt(0)
        result.put(commitId, rs.getString(1)!!)
      }
    }

    return result
  }

  override fun getCommitterForCommit(commitId: Int): VcsUser? {
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

  override fun getAuthorTime(commitIds: Collection<Int>): Map<Int, Long> {
    return getTime(commitIds, true)
  }

  override fun getCommitTime(commitIds: Collection<Int>): Map<Int, Long> {
    return getTime(commitIds, false)
  }

  private fun getTime(commitIds: Collection<Int>, isAuthorTime: Boolean): Map<Int, Long> {
    val result = hashMapOf<Int, Long>()
    val paramBinder = ObjectBinder(paramCount = 0)
    val inClause = commitIds.toInClause()
    val sql = "select commitId, ${if (isAuthorTime) "authorTime" else "commitTime"}  from log where commitId in $inClause"

    connection.prepareStatement(sql, paramBinder).use { statement ->
      val rs = statement.executeQuery()
      while (rs.next()) {
        val commitId = rs.getInt(0)
        result.put(commitId, rs.getLong(1))
      }
    }

    return result
  }

  override fun getParents(commitId: Int): IntArray {
    return connectionManager.selectParent.use { statement, binder ->
      binder.bind(commitId)
      readIntArray(statement)
    }
  }

  override fun getParents(commitIds: Collection<Int>): Map<Int, List<Hash>> {
    val result = hashMapOf<Int, MutableList<Hash>>()
    val paramBinder = ObjectBinder(paramCount = 0)
    val inClause = commitIds.toInClause()
    val sql = "select p.rowid, c.hash from commit_hashes c inner join parent p on p.parent = c.rowid where p.commitId in $inClause"

    connection.prepareStatement(sql, paramBinder).use { statement ->
      val rs = statement.executeQuery()
      while (rs.next()) {
        val commitId = rs.getInt(0)
        val hashes = result.computeIfAbsent(commitId) { mutableListOf() }
        hashes.add(rs.getString(1)!!.let(HashImpl::build))
      }
    }

    return result
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
    val oldConnectionManager = connectionManager
    connectionManager = connectionManager.recreate().also { Disposer.register(disposable, it) }
    Disposer.dispose(oldConnectionManager)
  }

  override fun getAuthorForCommit(commitId: Int): VcsUser? {
    val paramBinder = IntBinder(paramCount = 1)
    connection.prepareStatement("select name, email from user where commitId = ? and isCommitter = 0", paramBinder)
      .use { statement ->
        paramBinder.bind(commitId)
        val rs = statement.executeQuery()
        if (!rs.next()) {
          return null
        }

        return userRegistry.createUser(rs.getString(0)!!, rs.getString(1)!!)
      }
  }

  override fun getAuthorForCommits(commitIds: Iterable<Int>): Map<Int, VcsUser> {
    return getAuthorOrCommitter(commitIds, true)
  }

  override fun getCommitterForCommits(commitIds: Iterable<Int>): Map<Int, VcsUser> {
    return getAuthorOrCommitter(commitIds, false)
  }

  private fun getAuthorOrCommitter(commitIds: Iterable<Int>, isAuthor: Boolean): Map<Int, VcsUser> {
    val result = hashMapOf<Int, VcsUser>()
    val paramBinder = ObjectBinder(paramCount = 0)
    val inClause = commitIds.toInClause()
    val isCommitter = if (isAuthor) 0 else 1
    val sql = "select commitId, name, email from user where isCommitter = $isCommitter and commitId in $inClause"

    connection.prepareStatement(sql, paramBinder).use { statement ->
      val rs = statement.executeQuery()
      while (rs.next()) {
        val commitId = rs.getInt(0)
        result.put(commitId, userRegistry.createUser(rs.getString(1)!!, rs.getString(2)!!))
      }
    }
    return result
  }

  override fun getCommitsForUsers(users: Set<VcsUser>): IntSet {
    val commitIds = IntOpenHashSet()

    for (user in users) {
      connectionManager.selectCommitsForUserPool.use { statement, binder ->
        binder.bind(user.name, user.email)
        val rs = statement.executeQuery()
        while (rs.next()) {
          commitIds.add(rs.getInt(0))
        }
      }
    }

    return commitIds
  }

  private fun getCommitterForCommit(commitId: Int, isCommitter: Int): VcsUser? {
    return connectionManager.selectCommitterForCommitPool.use { statement, binder ->
      binder.bind(commitId, isCommitter)
      val rs = statement.executeQuery()
      if (rs.next()) {
        userRegistry.createUser(rs.getString(0)!!, rs.getString(1)!!)
      }
      else {
        null
      }
    }
  }

  override fun flush() {
  }

  override fun iterateChangesInCommits(root: VirtualFile, path: FilePath, consumer: ObjIntConsumer<List<ChangeKind>>) {
    connectionManager.runUnderReadonlyConnection { connection ->
      val position = rootsToPosition.getInt(root)
      val relativePath = LightFilePath(root, path).relativePath
      val changesInCommit = Int2ObjectOpenHashMap<MutableList<ChangeKind>>()
      val paramBinder = ObjectBinder(paramCount = 2)
      connection.prepareStatement(
        "select commitId, kind from path as p join path_change as c on p.rowid = c.pathId where p.position = ? and p.relativePath = ?",
        paramBinder).use { statement ->
        paramBinder.bind(position, relativePath)

        val rs = statement.executeQuery()
        while (rs.next()) {
          val changes = changesInCommit.computeIfAbsent(rs.getInt(0), IntFunction { arrayListOf() })
          changes.add(ChangeKind.getChangeKindById(rs.getInt(1).toByte()))
        }
      }

      for ((commitId, changes) in changesInCommit) {
        consumer.accept(changes, commitId)
      }
    }
  }

  override fun findRename(parent: Int, child: Int, root: VirtualFile, path: FilePath, isChildPath: Boolean): EdgeData<FilePath?>? {
    val renames = connectionManager.selectRename.use { statement, binder ->
      binder.bind(parent, child)
      readIntArray(statement)
    }
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

  override fun getPathsEncoder(): VcsLogIndexer.PathsEncoder {
    return VcsLogIndexer.PathsEncoder { root, relativePath, _ ->
      val position = rootsToPosition.getInt(root)
      val pathId = getPathId(root, relativePath)
      if (pathId != null) {
        pathId
      }
      else {
        connectionManager.insertPathPool.use { statement, binder ->
          binder.bind(position, relativePath)
          statement.selectNotNullInt()
        }
      }
    }
  }

  private fun getPath(pathId: Int): LightFilePath? {
    return connectionManager.selectPathPool.use { statement, binder ->
      binder.bind(pathId)

      val rs = statement.executeQuery()
      if (rs.next()) {
        LightFilePath(sortedRoots.get(rs.getInt(0)), rs.getString(1)!!)
      }
      else {
        null
      }
    }
  }

  private fun getPathId(filePath: LightFilePath): Int {
    return getPathIdOrFail(filePath.root, filePath.relativePath)
  }

  private fun getPathIdOrFail(root: VirtualFile, relativePath: String): Int {
    return getPathId(root, relativePath) ?: throw IOException("Path ${root} with relativePath = ${relativePath} not stored")
  }

  private fun getPathId(root: VirtualFile, relativePath: String): Int? {
    val position = rootsToPosition.getInt(root)
    return connectionManager.selectPathIdPool.use { statement, binder ->
      binder.bind(position, relativePath)
      statement.selectInt()
    }
  }

  override fun getCommitIndex(hash: Hash, root: VirtualFile): Int {
    val position = rootsToPosition.getInt(root)
    val commitId = getCommitId(position, hash)
    if (commitId != null) {
      return commitId
    }

    return connectionManager.insertCommitPool.use { statement, binder ->
      binder.bind(position, hash.asString())
      statement.selectNotNullInt()
    }
  }

  private fun getCommitId(position: Int, hash: Hash): Int? {
    return connectionManager.selectCommitPool.use { statement, binder ->
      binder.bind(position, hash.asString())
      statement.selectInt()
    }
  }

  override fun getCommitIds(commitIds: Collection<Int>): Map<Int, CommitId> {
    val paramBinder = ObjectBinder(paramCount = 0)
    val result = Int2ObjectOpenHashMap<CommitId>()
    val inClause = commitIds.toInClause()
    val sql = "select rowid, position, hash from commit_hashes where rowid in $inClause"

    connection.prepareStatement(sql, paramBinder).use { statement ->
      val rs = statement.executeQuery()
      while (rs.next()) {
        val commitId = rs.getInt(0)
        val root = sortedRoots.get(rs.getInt(1))
        val hash = rs.getString(2)!!.let(HashImpl::build)
        result.put(commitId, CommitId(hash, root))
      }
    }

    return result
  }

  override fun getCommitId(commitIndex: Int): CommitId? {
    val result = connectionManager.runUnderReadonlyConnection { connection ->
      val paramBinder = IntBinder(paramCount = 1)
      connection.prepareStatement("select position, hash from commit_hashes where rowid = ?", paramBinder).use { statement ->
        paramBinder.bind(commitIndex)

        val rs = statement.executeQuery()
        if (rs.next()) {
          val root = sortedRoots.get(rs.getInt(0))
          val hash = rs.getString(1)!!.let(HashImpl::build)
          return@runUnderReadonlyConnection CommitId(hash, root)
        }
      }
      null
    }
    if (result == null) {
      errorHandler.handleError(VcsLogErrorHandler.Source.Storage, RuntimeException("Unknown commit index: $commitIndex"))
    }
    return result
  }

  override fun containsCommit(id: CommitId): Boolean {
    val position = rootsToPosition.getInt(id.root)
    val hashStr = id.hash.asString()
    return !connection.selectBoolean("select not exists (select 1 from commit_hashes where position = ? and hash = ?)",
                                     arrayOf(position, hashStr))
  }

  override fun getRefIndex(ref: VcsRef): Int {
    val position = rootsToPosition.getInt(ref.root)
    val hashStr = ref.commitHash.asString()
    val name = ref.name
    val refTypeSerializer = VcsRefTypeSerializer()
    logProviders[ref.root]!!.referenceManager.serialize(refTypeSerializer, ref.type)
    val type = refTypeSerializer.readInt()
    val commitId = getCommitId(position, ref.commitHash)
    if (commitId == null) {
      return connectionManager.insertMoreCommitPool.use { statement, binder ->
        binder.bind(position, hashStr, name, type)
        statement.selectNotNullInt()
      }
    }
    else {
      return connectionManager.updateCommitPool.use { statement, binder ->
        binder.bind(name, type, position, hashStr)
        statement.selectNotNullInt()
      }
    }
  }

  override fun getVcsRef(refIndex: Int): VcsRef? {
    return connectionManager.runUnderReadonlyConnection { connection ->
      val paramBinder = IntBinder(paramCount = 1)
      connection.prepareStatement("select position, hash, name, type from commit_hashes where rowid = ?", paramBinder).use { statement ->
        paramBinder.bind(refIndex)

        val rs = statement.executeQuery()
        if (rs.next()) {
          val root = sortedRoots.get(rs.getInt(0))
          val hash = rs.getString(1)!!.let(HashImpl::build)
          val name = rs.getString(2)!!
          val refTypeSerializer = VcsRefTypeSerializer().apply { writeInt(rs.getInt(3)) }
          val type = logProviders[root]!!.referenceManager.deserialize(refTypeSerializer)
          return@runUnderReadonlyConnection VcsRefImpl(hash, name, type, root)
        }
      }
      null
    }
  }

  override fun iterateCommits(consumer: Predicate<in CommitId>) {
    connectionManager.runUnderReadonlyConnection { connection ->
      val paramBinder = IntBinder(paramCount = 0)
      connection.prepareStatement("select position, hash from commit_hashes", paramBinder).use { statement ->
        val rs = statement.executeQuery()
        while (rs.next()) {
          val root = sortedRoots.get(rs.getInt(0))
          val hash = rs.getString(1)!!.let(HashImpl::build)
          if (!consumer.test(CommitId(hash, root))) {
            break
          }
        }
      }
    }
  }
}

@Suppress("SqlResolve")
private class SqliteVcsLogWriter(private val connection: SqliteConnection, private val storage: VcsLogStorage) : VcsLogWriter {

  init {
    connection.beginTransaction()
  }

  private val statementCollection = StatementCollection(connection)

  private val logBatch = statementCollection.prepareStatement("""
    insert into log(commitId, message, authorTime, commitTime, isCommitter) 
    values(?, ?, ?, ?, ?) 
    on conflict(commitId) do update set message=excluded.message
    """, ObjectBinder(paramCount = 5, batchCountHint = 1024)).binder
  private val userBatch = statementCollection.prepareStatement("""
    insert into user(commitId, isCommitter, name, email) 
    values(?, ?, ?, ?) 
    """, ObjectBinder(paramCount = 4, batchCountHint = 256)).binder

  // first `delete`, then `insert` - `delete` statement must be added to the statement collection first
  private val parentDeleteStatement = statementCollection.prepareIntStatement("delete from parent where commitId = ?")
  private val parentStatement = statementCollection.prepareIntStatement("insert into parent(commitId, parent) values(?, ?)")

  private val renameDeleteStatement = statementCollection.prepareIntStatement("delete from rename where parent = ? and child = ?")
  private val renameStatement = statementCollection.prepareIntStatement("insert into rename(parent, child, rename) values(?, ?, ?)")

  private val changeStatement = statementCollection.prepareIntStatement("insert into path_change(commitId, pathId, kind) values(?, ?, ?)")

  override fun putCommit(commitId: Int, details: VcsLogIndexer.CompressedDetails) {
    putPathChanges(commitId, details)
    putParents(commitId, details.root, details.parents)

    val isCommitter = if (details.author == details.committer) 0 else 1
    if (isCommitter == 1) {
      userBatch.bind(commitId, isCommitter, details.committer.name, details.committer.email)
      userBatch.addBatch()
    }
    userBatch.bind(commitId, 0, details.author.name, details.author.email)
    userBatch.addBatch()

    logBatch.bind(commitId, details.fullMessage, details.authorTime, details.commitTime, isCommitter)
    logBatch.addBatch()
  }

  private fun putParents(commitId: Int, root: VirtualFile, parents: List<Hash>) {
    // clear old if any
    parentDeleteStatement.binder.bind(commitId)
    parentDeleteStatement.addBatch()

    for (parent in parents) {
      parentStatement.binder.bind(commitId, storage.getCommitIndex(parent, root))
      parentStatement.addBatch()
    }
  }

  private fun putRename(parent: Int, child: Int, renames: IntArray) {
    renameDeleteStatement.binder.bind(parent, child)
    renameDeleteStatement.addBatch()

    for (rename in renames) {
      renameStatement.binder.bind(parent, child, rename)
      renameStatement.addBatch()
    }
  }

  private fun putPathChanges(commitId: Int, details: VcsLogIndexer.CompressedDetails) {
    val changesToStore = collectChangesAndPutRenames(details)
    changeStatement.ensureCapacity(changesToStore.size)
    for (entry in changesToStore.int2ObjectEntrySet()) {
      val pathId = entry.intKey
      val changes = entry.value

      for (change in changes) {
        changeStatement.binder.bind(commitId, pathId, change.id.toInt())
        changeStatement.addBatch()
      }
    }
  }

  private fun collectChangesAndPutRenames(details: VcsLogIndexer.CompressedDetails): Int2ObjectOpenHashMap<List<ChangeKind>> {
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
        val commit = storage.getCommitIndex(details.id, details.root)
        val parent = storage.getCommitIndex(details.parents[parentIndex], details.root)

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

  override fun interrupt() = connection.interruptAndClose()
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

private fun Iterable<Int>.toInClause() = "(" + joinToString(separator = ",") { "'$it'" } + ")"

internal val VcsLogStorageBackend.isSqliteBackend: Boolean get() = this is SqliteVcsLogStorageBackend
