// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SqlResolve")

package com.intellij.ae.database.dbs

import com.intellij.ae.database.IdService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.util.ExceptionUtil
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.TestOnly
import org.jetbrains.sqlite.SqliteConnection
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isWritable
import kotlin.time.Duration.Companion.seconds

private val logger = logger<SqliteLazyInitializedDatabase>()

private const val MAX_CONNECTION_RETRIES_ALLOWED = 4

/**
 * This service provides access to an instance of [SqliteConnection]
 */
@Service
class SqliteLazyInitializedDatabase(private val cs: CoroutineScope) : ISqliteExecutor, ISqliteInternalExecutor {
  companion object {
    internal suspend fun getInstanceAsync() = serviceAsync<SqliteLazyInitializedDatabase>()
    internal fun getInstance() = ApplicationManager.getApplication().service<SqliteLazyInitializedDatabase>()
  }
  private val connectionMutex = Mutex()
  private var connectionAttempts = 0
  private var connection: SqliteConnection? = null
  private var metadata: SqliteDatabaseMetadata? = null

  private var retryMessageLogged = false

  private val actionsBeforeDatabaseDisposal = mutableListOf<suspend () -> Unit>()

  init {
    cs.launch {
      if (ApplicationManager.getApplication().isUnitTestMode) return@launch
      cs.awaitCancellationAndInvoke {
        logger.info("Database disposal started, ${actionsBeforeDatabaseDisposal.size} actions to perform")
        logger.runAndLogException {
          withTimeout(4.seconds) {
            @Suppress("TestOnlyProblems")
            doExecuteBeforeConnectionClosed()
          }
        }
        logger.runAndLogException {
          val myConnection = connectionMutex.withLock { connection }
          if (myConnection != null) {
            myConnection.close()
          }
          else {
            logger.info("Connection was null, so didn't close it")
          }
        }
        logger.info("Database disposal finished")
      }
    }
  }

  fun executeBeforeConnectionClosed(action: suspend () -> Unit) {
    actionsBeforeDatabaseDisposal.add(action)
  }

  private suspend fun doExecuteBeforeConnectionClosed() {
    for (action in actionsBeforeDatabaseDisposal) {
      action()
    }
  }

  @TestOnly
  suspend fun closeDatabase() {
    doExecuteBeforeConnectionClosed()
    connectionMutex.withLock { connection }?.close()
  }

  /**
   * Allows executing code with [SqliteConnection]
   */
  override suspend fun <T> execute(action: suspend (initDb: SqliteConnection, metadata: SqliteDatabaseMetadata) -> T): T? {
    val myConnectionAttempts = connectionMutex.withLock { connectionAttempts }
    if (myConnectionAttempts >= MAX_CONNECTION_RETRIES_ALLOWED && !retryMessageLogged) {
      logger.error("Max retries reached to init db")
      retryMessageLogged = true
      return null
    }
    val myPair = connectionMutex.withLock {
      try {
        getOrInitConnection()
      }
      catch (t: Throwable) {
        logger.error(t)
        null
      }
    }

    if (myPair == null) return null

    val (myConnection, myMetadata) = myPair

    check(myConnection.isOpen()) { "Database is not open" }

    return withContext(Dispatchers.IO) {
      action(myConnection, myMetadata)
    }
  }

  override suspend fun <T> execute(action: suspend (initDb: SqliteConnection) -> T): T? {
    return execute { initDb, metadata ->
      action(initDb)
    }
  }

  private fun getOrInitConnection(): Pair<SqliteConnection, SqliteDatabaseMetadata> {
    val currentConnection = connection
    val currentMetadata = metadata
    if (currentConnection != null && currentMetadata == null) {
      logger.error("Metadata is null while connection is not")
    }
    return if (currentConnection != null && currentMetadata != null) {
      currentConnection to currentMetadata
    }
    else {
      logger.info("Initializing database connection")
      logger.trace(ExceptionUtil.currentStackTrace())
      ++connectionAttempts
      val path = getDatabasePath()
      val isNewFile = path == null || !path.exists()
      val newConnection = SqliteConnection(path, false)
      val newMetadata = SqliteDatabaseMetadata(newConnection, isNewFile)

      connection = newConnection
      metadata = newMetadata

      return newConnection to newMetadata
    }
  }

  private fun SqliteConnection.isOpen() = !isClosed

  private fun getDatabasePath(): Path? {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      val tempPath = System.getProperty("ae.database.path")
      assert(tempPath != null) { "No path set to temp database" }
      return Path.of(tempPath)
    }

    val fileName = "ae_${IdService.getInstance().id}.db"

    val folder = PathManager.getCommonDataPath().resolve("IntelliJ")
    val desiredDatabasePath = folder.resolve(fileName)

    // javadoc for Files.isWritable(): "Checks if the **file located by this path exists** and is writable"
    if ((desiredDatabasePath.exists() && !desiredDatabasePath.isWritable())
        || !desiredDatabasePath.exists() && !folder.isWritable()
        || System.getProperty("ae.database.forceConfigFolder")?.toBoolean() == true) {
      logger.warn("Requested file {${folder.absolutePathString()} is not writable")
      return PathManager.getConfigDir().resolve(fileName)
    }

    folder.createDirectories()
    return desiredDatabasePath
  }
}
