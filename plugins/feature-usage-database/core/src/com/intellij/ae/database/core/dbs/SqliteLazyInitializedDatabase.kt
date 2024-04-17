// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SqlResolve")
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.ae.database.core.dbs

import com.intellij.ae.database.core.IdService
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.PlatformUtils
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.*
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.sqlite.SqliteConnection
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.exists
import kotlin.io.path.isWritable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = logger<SqliteLazyInitializedDatabase>()

private const val MAX_CONNECTION_RETRIES_ALLOWED = 5

/**
 * This service provides access to an instance of [SqliteConnection]
 */
@Service
class SqliteLazyInitializedDatabase(private val cs: CoroutineScope) : ISqliteExecutor, ISqliteInternalExecutor {
  companion object {
    internal suspend fun getInstanceAsync() = serviceAsync<SqliteLazyInitializedDatabase>()
    internal fun getInstance() = ApplicationManager.getApplication().service<SqliteLazyInitializedDatabase>()
  }

  private class ConnectionHolder(val connection: SqliteConnection, val metadata: SqliteDatabaseMetadata)
  private sealed class State {
    data object NotInitialized : State()
    class Active(val def: Deferred<ConnectionHolder>) : State()
    class Cancelling(val def: Deferred<ConnectionHolder>, val job: Job) : State()
    data object Locked : State()
  }

  private val databasePath by lazy { createDatabasePath() }
  private val connectionAttempts = AtomicInteger(0)
  // private val lastAccessTime = MutableStateFlow<Long>(0)
  private val myConnection = AtomicReference<State>(State.NotInitialized)
  private val actionsBeforeDatabaseDisposal = mutableListOf<suspend (isFinal: Boolean) -> Unit>()
  private val closeDispatcher = Dispatchers.IO.limitedParallelism(1)

  private val loggingAttempt = AtomicInteger(0)

  init {
    if (System.getProperty("ae.database.fullLock")?.toBoolean() == true) {
      myConnection.set(State.Locked)
    }

    cs.launch {
      while (true) {
        delay(6.minutes)
        mainClosingLogic(15.seconds, "every 6 min", false)
      }
    }

    cs.launch {
      try {
        awaitCancellation()
      }
      finally {
        withContext(NonCancellable) {
          mainClosingLogic(6.seconds, "scope cancellation", true)
        }
      }
    }
  }

  private var isFinalClosed = false
  private suspend fun CoroutineScope.mainClosingLogic(timeout: Duration, reason: String, isFinal: Boolean) {
    val shouldLog = isFinal || loggingAttempt.getAndIncrement() % 10 == 0
    if (isFinalClosed) {
      logger.info("Close database for reason \"${reason}\" ignored: already closed.")
    }

    if (shouldLog) {
      logger.info("Starting saving database (reason: $reason)")
    }
    try {
      withTimeout(timeout) {
        withContext(closeDispatcher) {
          closeDatabaseImpl(isFinal, shouldLog)?.join()
          if (shouldLog) {
            logger.info("Saving completed (reason: $reason)")
          }
        }
      }
    }
    catch (t: TimeoutCancellationException) {
      if (PlatformUtils.isPyCharm()) {
        // for some reason PyCharm and DataSpell installers don't start `closeDatabaseImpl()` in time, but only a minute later
        // this is not critical and no data will be (hopefully) lost, but the fix will be impletented a bit later
        logger.warn("Saving timeout (saving reason: $reason)\n${dumpCoroutines(this)}", t)
        logger.warn(dumpCoroutines(this))
      }
      else {
        logger.error("Saving timeout (saving reason: $reason)\n${dumpCoroutines(this)}", t)
      }
    }
    catch (t: Throwable) {
      logger.error("Saving failed (saving reason: $reason)\n${dumpCoroutines(this)}", t)
    }
    finally {
      if (isFinal) isFinalClosed = true
    }
  }

  fun executeBeforeConnectionClosed(action: suspend (isFinal: Boolean) -> Unit) {
    actionsBeforeDatabaseDisposal.add(action)
  }

  @VisibleForTesting
  suspend fun closeDatabase() {
    coroutineScope {
      mainClosingLogic(16.seconds, "in test", true)
    }
  }

  @Suppress("OPT_IN_USAGE")
  private fun closeDatabaseImpl(isFinal: Boolean, shouldLog: Boolean): Job? {
    // service scope is dead at this point, need to use GlobalScope
    // todo: not true on temp close
    fun launchJob(action: suspend CoroutineScope.() -> Unit): Job =
      GlobalScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY, action)

    while (true) {
      when (val state = myConnection.get()) {
        is State.NotInitialized -> {
          if (!isFinal) return null
          return launchJob {
            logger.info("Opening connection for final termination.")
            val connection = getConn2(this)
            if (connection != null) {
              logger.info("Connection opened, performing termination.")
              close(connection, isFinal = true, shouldLog = shouldLog)
            } else {
              logger.info("Connection open failure.")
            }
          }
        }
        is State.Active -> {
          val job = launchJob {
            close(state.def.await(), isFinal, shouldLog = shouldLog)
          }
          val newState = State.Cancelling(state.def, job)
          if (myConnection.compareAndSet(state, newState)) {
            job.invokeOnCompletion {
              check(myConnection.compareAndSet(newState, State.NotInitialized))
            }
            return job
          }
          else {
            job.cancel()
          }
        }
        is State.Cancelling -> {
          return state.job
        }
        is State.Locked -> {
          return null
        }
      }
    }
  }

  private suspend fun doExecuteBeforeConnectionClosed(isFinal: Boolean) {
    for (action in actionsBeforeDatabaseDisposal) {
      action(isFinal)
    }
  }

  /**
   * Allows executing code with [SqliteConnection]
   */
  override suspend fun <T> execute(action: suspend (initDb: SqliteConnection, metadata: SqliteDatabaseMetadata) -> T): T? {
    val conn = withContext(Dispatchers.IO) {
      getConn2()
    }

    if (conn == null) {
      return null
    }

    return action(conn.connection, conn.metadata)
  }

  private suspend fun getConn2(scope: CoroutineScope = cs): ConnectionHolder? {
    while (true) {
      when (val state = myConnection.get()) {
        State.NotInitialized -> {
          val connectionHolderDeferred = CompletableDeferred<ConnectionHolder>(parent = scope.coroutineContext.job)
          if (!myConnection.compareAndSet(state, State.Active(connectionHolderDeferred))) {
            connectionHolderDeferred.cancel()
            continue
          }
          val conn = kotlin.runCatching {
            val dbPath = databasePath
            logger.info("Database path: $dbPath")
            val isNewFile = dbPath == null || !dbPath.exists()
            val newConnection = SqliteConnection(dbPath, false)
            val newMetadata = SqliteDatabaseMetadata(newConnection, isNewFile)
            ConnectionHolder(newConnection, newMetadata)
          }
          if (!connectionHolderDeferred.completeWith(conn)) {
            if (conn.isSuccess) {
              conn.getOrThrow().connection.close()
            }
            else {
              if (connectionAttempts.getAndIncrement() >= MAX_CONNECTION_RETRIES_ALLOWED) {
                myConnection.compareAndSet(state, State.Locked)
              }
            }
          }
          return connectionHolderDeferred.await()
        }
        is State.Active -> {
          return state.def.await()
        }
        is State.Cancelling -> {
          // during cancellation there might be calls to DB
          return state.def.await()
        }
        is State.Locked -> {
          return null
        }
      }
    }
  }

  override suspend fun <T> execute(action: suspend (initDb: SqliteConnection) -> T): T? {
    return execute { initDb, metadata ->
      action(initDb)
    }
  }

  private suspend fun close(connectionHolder: ConnectionHolder, isFinal: Boolean, shouldLog: Boolean) {
    if (shouldLog) {
      logger.info("close start")
    }
    connectionHolder.connection.use {
      doExecuteBeforeConnectionClosed(isFinal)
    }
    check(connectionHolder.connection.isClosed)
    if (shouldLog) {
      logger.info("close end")
    }
  }

  private fun createDatabasePath(): Path? {
    val tempPath = System.getProperty("ae.database.path")
    if (tempPath != null) {
      return Path.of(tempPath)
    }

    val majorVersion = ApplicationInfo.getInstance().build.baselineVersion
    // todo if settings are migrated, hash is the same
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
    val intellijFolder = commonFolder.resolve("IntelliJ")
    val desiredDatabasePath = intellijFolder.resolve(fileName)

    if (System.getProperty("ae.database.forceConfigFolder")?.toBoolean() == true) {
      return null
    }

    // folder where the file is located is required to be writable for journal creation
    if (intellijFolder.exists() && !intellijFolder.isWritable()) {
      logger.warn("Folder $intellijFolder exist, but not writable")
      return null
    }

    if (!intellijFolder.exists() && !commonFolder.isWritable()) {
      logger.warn("Folder $intellijFolder does not exist and $commonFolder is not writable")
      return null
    }

    if (desiredDatabasePath.exists() && !desiredDatabasePath.isWritable()) {
      logger.warn("Db file $desiredDatabasePath exists, but not writable")
      return null
    }

    if (!intellijFolder.exists()) {
      intellijFolder.createDirectories()
    }

    return desiredDatabasePath
  }

  private fun createDatabasePathStoreInConfigFolder(fileName: String, mask: String): Path {
    val configFolder = PathManager.getConfigDir()

    val databasePath = configFolder.resolve(fileName)

    return databasePath
  }
}
