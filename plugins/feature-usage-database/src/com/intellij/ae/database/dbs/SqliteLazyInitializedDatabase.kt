// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SqlResolve")

package com.intellij.ae.database.dbs

import com.intellij.ae.database.IdService
import com.intellij.ae.database.utils.InstantUtils
import com.intellij.openapi.application.ApplicationInfo
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
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.isWritable
import kotlin.time.Duration.Companion.minutes
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

  private val databasePath by lazy { createDatabasePath() }

  private val connectionMutex = Mutex()
  private var connectionAttempts = 0
  private var lastConnectionAt: Instant = InstantUtils.Now
  private var lastSaveAt: Instant = InstantUtils.Now
  private var connection: SqliteConnection? = null
  private var metadata: SqliteDatabaseMetadata? = null

  private var retryMessageLogged = false

  private val actionsBeforeDatabaseDisposal = mutableListOf<suspend () -> Unit>()

  init {
    cs.launch {
      awaitCancellationAndInvoke {
        logger.info("Database disposal started, ${actionsBeforeDatabaseDisposal.size} actions to perform")
        logger.runAndLogException {
          withTimeout(4.seconds) {
            @Suppress("TestOnlyProblems")
            doExecuteBeforeConnectionClosed()
          }
        }
        logger.runAndLogException {
          connectionMutex.withLock {
            closeConnection()
          }
        }
        logger.info("Database disposal finished")
      }
    }

    cs.launch {
      logger.runAndLogException {
        while (isActive) {
          // Close database connection after five minutes of inactivity or 10 minutes of last save
          delay(3.minutes)
          connectionMutex.withLock {
            if (connection != null) {
              val lastConnectionNn = lastConnectionAt
              val lastSaveNn = lastSaveAt

              val timeDiffLastInit = Duration.between(lastConnectionNn, InstantUtils.Now)
              val timeDiffLastSave = Duration.between(lastSaveNn, InstantUtils.Now)
              if (timeDiffLastInit >= Duration.ofMinutes(5) || timeDiffLastSave >= Duration.ofMinutes(10)) {
                logger.info("Closing connection due to inactivity")
                closeConnection()
                lastSaveAt = InstantUtils.Now
              }
            }
          }
        }
      }
    }
  }

  fun executeBeforeConnectionClosed(action: suspend () -> Unit) {
    actionsBeforeDatabaseDisposal.add(action)
  }

  @TestOnly
  suspend fun closeDatabaseInTest() {
    doExecuteBeforeConnectionClosed()
    connectionMutex.withLock {
      closeConnection()
    }
  }

  private suspend fun doExecuteBeforeConnectionClosed() {
    for (action in actionsBeforeDatabaseDisposal) {
      action()
    }
  }

  /**
   * Must run under [connectionMutex]
   */
  private fun closeConnection(shouldLog: Boolean = true) {
    assert(connectionMutex.isLocked)

    val conn = connection
    if (conn != null) {
      conn.close()
      connectionAttempts = 0
      connection = null
      metadata = null
    }
    else if (shouldLog) {
      logger.info("Connection was null, so didn't close it")
    }
  }

  /**
   * Allows executing code with [SqliteConnection]
   */
  override suspend fun <T> execute(action: suspend (initDb: SqliteConnection, metadata: SqliteDatabaseMetadata) -> T): T? {
    val myConnectionAttempts = connectionMutex.withLock { connectionAttempts }
    if (myConnectionAttempts >= MAX_CONNECTION_RETRIES_ALLOWED) {
      if (!retryMessageLogged) {
        logger.error("Max retries reached to init db")
        retryMessageLogged = true
      }

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

  /**
   * Must run under [connectionMutex]
   */
  private fun getOrInitConnection(): Pair<SqliteConnection, SqliteDatabaseMetadata> {
    val currentConnection = connection
    val currentMetadata = metadata
    if (currentConnection != null && currentMetadata == null) {
      logger.error("Metadata is null while connection is not (not a fatal error)")
    }
    else if (currentConnection == null && currentMetadata != null) {
      logger.error("Connection is null while metadata is not (not a fatal error)")
    }
    return if (currentConnection != null && currentMetadata != null) {
      lastConnectionAt = InstantUtils.Now
      currentConnection to currentMetadata
    }
    else {
      logger.info("Initializing database connection")
      logger.trace(ExceptionUtil.currentStackTrace())
      ++connectionAttempts
      val dbPath = databasePath
      val isNewFile = dbPath == null || !dbPath.exists()
      val newConnection = SqliteConnection(dbPath, false)
      val newMetadata = SqliteDatabaseMetadata(newConnection, isNewFile)

      connection = newConnection
      metadata = newMetadata
      lastConnectionAt = InstantUtils.Now
      lastSaveAt = InstantUtils.Now

      return newConnection to newMetadata
    }
  }

  private fun SqliteConnection.isOpen() = !isClosed

  private fun createDatabasePath(): Path? {
    val tempPath = System.getProperty("ae.database.path")
    if (tempPath != null) {
      return Path.of(tempPath)
    }

    val majorVersion = ApplicationInfo.getInstance().build.baselineVersion
    val fileName = "ae_${IdService.getInstance().id}-$majorVersion.db"
    val fileMask = "ae_${IdService.getInstance().id}-*.db"

    // Attempt 1: store db file in common folder for all ides
    val attempt1 = try {
      createDatabasePathStoreInCommonFolder(fileName, fileMask)
    }
    catch (t: Throwable) {
      logger.error("Could not get path in common folder", t)
      null
    }
    if (attempt1 != null) {
      return attempt1
    }

    // Attempt 2: store db file in IDE's `config` directory
    val attempt2 = createDatabasePathStoreInConfigFolder(fileName, fileMask)

    return attempt2
  }

  private fun createDatabasePathStoreInCommonFolder(fileName: String, mask: String): Path? {
    val commonFolder = PathManager.getCommonDataPath()
    val folder = commonFolder.resolve("IntelliJ")
    val desiredDatabasePath = folder.resolve(fileName)

    if (System.getProperty("ae.database.forceConfigFolder")?.toBoolean() == true) {
      return null
    }

    if (desiredDatabasePath.exists() && !desiredDatabasePath.isWritable()) {
      logger.error("Desired file $desiredDatabasePath exists, but not writable")
      return null
    }

    if (!desiredDatabasePath.exists() && !commonFolder.isWritable()) {
      logger.error("Desired file $desiredDatabasePath does not exist and $commonFolder is not writable")
      return null
    }

    performMigrationIfNeeded(folder, fileName, mask)

    folder.createDirectories()

    return desiredDatabasePath
  }

  private fun createDatabasePathStoreInConfigFolder(fileName: String, mask: String): Path {
    val configFolder = PathManager.getConfigDir()
    performMigrationIfNeeded(configFolder, fileName, mask)

    return configFolder.resolve(fileName)
  }

  // should be executed before dir creation
  private fun performMigrationIfNeeded(parentDir: Path, currentFileName: String, mask: String) {
    if (!parentDir.exists() || parentDir.resolve(currentFileName).exists()) {
      return
    }

    val fileToMigrate = Files.newDirectoryStream(parentDir, mask).use { paths ->
      paths.maxByOrNull { p1 ->
        getBuildNumber(mask, p1.fileName.toString())
      }
    }

    if (fileToMigrate == null) {
      return
    }

    logger.info("Found file to migrate: $fileToMigrate")
  }

  private fun getBuildNumber(mask: String, fileName: String): Int {
    return try {
      val prefix = mask.substringBefore('*')
      val suffix = mask.substringAfter('*')

      fileName.removePrefix(prefix).removeSuffix(suffix).toInt()
    }
    catch (t: Throwable) {
      logger.error("Failed to parse file version")
      -1
    }
  }
}
