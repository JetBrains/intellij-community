// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.io.storages.database.DurableMapImplementation
import com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_SEPARATELY
import com.intellij.platform.util.io.storages.database.spi.metrics.DatabaseMetrics
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.impl.storage.durablemap.database.DurableDatabaseIndexLayoutProvider
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorIntegerDescriptor
import com.intellij.util.io.KeyDescriptor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.LongAdder
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.logging.Level

//@formatter:off
private const val PROPERTY_PREFIX                   = "DurableDatabaseIndexLayoutLoadTest."
private const val DATABASE_LOG_CATEGORY             = "#com.intellij.platform.util.io.storages.database"
private const val DEFAULT_DEBUG_DATABASE_LOGS       = true
private const val LOCK_STRIPES                      = 256

private const val DEFAULT_INDEX_COUNT               = 32
private const val DEFAULT_KEYS_PER_INDEX            = 50_000
private const val DEFAULT_WRITER_COUNT              = 2
private const val DEFAULT_READER_COUNT              = 4

private const val DEFAULT_ROUNDS_COUNT              = 4
private const val DEFAULT_ROUND_DURATION_SECONDS    = 15 * 60

private const val DEFAULT_DELETE_PERCENT            = 95   // % of records to delete before session close
private const val DEFAULT_STEADY_STATE_ROUNDS_COUNT = 5
private const val DEFAULT_REPORT_SECONDS            = 60
private const val DEFAULT_FLUSH_SECONDS             = 60
//@formatter:on


/**
 * Runs a manual, long load against many index layouts in one database.
 * The test retains and reuses its database directory between runs.
 * Each round deletes a configured share of entries before it closes the database.
 * After the load rounds, the test repeatedly reopens the database without a workload.
 */
@EnabledIfSystemProperty(named = "ijpl.255591.load.enabled", matches = "true")
class DurableDatabaseIndexLayoutLoadTest {

  // Test-load scenario:
  // 1) Opens a Database (creates a new one, if not exist) from path=sys('DurableDatabaseIndexLayoutLoadTest.dir')
  // 2) Opens/creates 32 IndexStorageLayouts (=DEFAULT_INDEX_COUNT)
  // 4) Runs load round for 15 min (=DEFAULT_DURATION_SECONDS).
  //    - 50_000 keys per index (=DEFAULT_KEYS_PER_INDEX), Pareto-like distribution: 80% operations use 20% keys
  //    - multithreaded: 4 writers create/update records, 8 readers read and verify them
  //    - periodic flush()
  // 5) Finalizes the load round:
  //    - checks (final state) records in indexes;
  //    - deletes ~80% records (=DEFAULT_DELETE_PERCENT);
  //    - closes IndexStorageLayout & Database;
  // 6) Re-opens the Database:
  //    - checks deleted records are indeed deleted;
  //    - re-inserts records deleted on previous turn;
  // 8) Start next round (=DEFAULT_ROUNDS_COUNT);
  // 9) After ${DEFAULT_ROUNDS_COUNT} rounds:
  //    - run ${DEFAULT_STEADY_STATE_ROUNDS_COUNT} DB open/close cycles
  //    - goal is to see DB steady-state after its housekeeping gets enough time


  private data class LoadConfig(
    val directory: Path,
    val seed: Long,
    val indexCount: Int,
    val keysPerIndex: Int,
    val writerCount: Int,
    val readerCount: Int,
    val roundsCount: Int,
    val steadyStateRoundsCount: Int,
    val deletePercent: Int,
    val debugDatabaseLogs: Boolean,
    val durationSeconds: Int,
    val reportSeconds: Int,
    val flushSeconds: Int,
    val invertedMapImplementation: DurableMapImplementation,
  ) {
    companion object {
      fun fromProperties(): LoadConfig {
        val path = requireNotNull(System.getProperty(PROPERTY_PREFIX + "dir")) {
          "Set ${PROPERTY_PREFIX}dir to a database directory"
        }
        val indexCount = intProperty("indexes", DEFAULT_INDEX_COUNT)
        val writerCount = intProperty("writers", DEFAULT_WRITER_COUNT)
        require(writerCount <= indexCount) { "Writer count must not exceed index count" }
        return LoadConfig(
          directory = Path.of(path),
          seed = System.getProperty(PROPERTY_PREFIX + "seed")?.toLong() ?: 255591L,
          indexCount = indexCount,
          keysPerIndex = intProperty("keysPerIndex", DEFAULT_KEYS_PER_INDEX),
          writerCount = writerCount,
          readerCount = intProperty("readers", DEFAULT_READER_COUNT),
          roundsCount = intProperty("rounds", DEFAULT_ROUNDS_COUNT),
          steadyStateRoundsCount = intProperty("steadyStateRounds", DEFAULT_STEADY_STATE_ROUNDS_COUNT),
          deletePercent = deletePercentProperty(),
          debugDatabaseLogs = debugDatabaseLogsProperty(),
          durationSeconds = intProperty("durationSeconds", DEFAULT_ROUND_DURATION_SECONDS),
          reportSeconds = intProperty("reportSeconds", DEFAULT_REPORT_SECONDS),
          flushSeconds = intProperty("flushSeconds", DEFAULT_FLUSH_SECONDS),
          invertedMapImplementation = mapImplementationProperty(),
        )
      }

      private fun intProperty(name: String, default: Int): Int {
        val value = System.getProperty(PROPERTY_PREFIX + name)?.toInt() ?: default
        require(value > 0) { "$name must be positive" }
        return value
      }

      private fun deletePercentProperty(): Int {
        val value = System.getProperty(PROPERTY_PREFIX + "deletePercent")?.toInt() ?: DEFAULT_DELETE_PERCENT
        require(value in 0..100) { "deletePercent must be between 0 and 100" }
        return value
      }

      private fun debugDatabaseLogsProperty(): Boolean {
        val value = System.getProperty(PROPERTY_PREFIX + "debugDatabaseLogs") ?: return DEFAULT_DEBUG_DATABASE_LOGS
        return value.toBooleanStrictOrNull() ?: throw IllegalArgumentException("debugDatabaseLogs must be true or false")
      }

      private fun mapImplementationProperty(): DurableMapImplementation {
        val propertyName = PROPERTY_PREFIX + "invertedMapImplementation"
        val value = requireNotNull(System.getProperty(propertyName, KEY_VALUE_SEPARATELY.name)) {
          "Set $propertyName to one of ${DurableMapImplementation.entries.joinToString()}"
        }
        return DurableMapImplementation.valueOf(value)
      }
    }
  }

  @Test
  fun runLoad(@TestDisposable disposable: Disposable) {
    val config = LoadConfig.fromProperties()
    if (config.debugDatabaseLogs) {
      enableDatabaseDebugLogging(disposable)
    }
    val directory = prepareDirectory(config.directory)
    println("Load start: \n" +
            "\tdirectory=$directory\n" +
            "\tseed=${config.seed}\n" +
            "\t${config.indexCount} indexes, ${config.keysPerIndex} keys per index\n" +
            "\twriters=${config.writerCount} readers=${config.readerCount}\n" +
            "\t${config.roundsCount} rounds ${config.durationSeconds} sec each\n" +
            "\tdelete ${config.deletePercent}% records after each round\n" +
            "\tat the end: ${config.steadyStateRoundsCount} steadyStateRounds\n" +
            "\treportSeconds=${config.reportSeconds} flushSeconds=${config.flushSeconds}\n" +
            "\tinvertedMapImplementation=${config.invertedMapImplementation}\n" +
            "\tdebugDatabaseLogs=${config.debugDatabaseLogs}")

    val extensions = List(config.indexCount) { LoadIndexExtension(it) }
    val values = Array(config.indexCount) { IntArray(config.keysPerIndex + 1) }
    val present = Array(config.indexCount) { BooleanArray(config.keysPerIndex + 1) }
    val generations = Array(config.indexCount) { IntArray(config.keysPerIndex + 1) }
    val keyLocks = Array(config.indexCount) { Array(LOCK_STRIPES) { Any() } }
    val totalEntries = config.indexCount.toLong() * config.keysPerIndex

    repeat(config.roundsCount) { roundIndex ->
      val round = roundIndex + 1
      val openStarted = System.nanoTime()
      var closeStartedNs = 0L
      openSession(directory, extensions, config.invertedMapImplementation).use { session ->
        val openMillis = millis(System.nanoTime() - openStarted)
        val preparationDetails = if (round == 1) {
          val existingEntries = initialize(session, config, values, present)
          "existingEntries=$existingEntries addedEntries=${totalEntries - existingEntries}"
        }
        else {
          verifyAll(session, config, values, present)
          "verifiedKeys=$totalEntries"
        }
        report("Load round #$round opened", directory, "open=$openMillis ms $preparationDetails", session)
        val restoredEntries = restoreMissing(session, config, values, present)
        if (restoredEntries > 0) {
          report("Load round #$round restored", directory, "restoredEntries=$restoredEntries", session)
        }

        runConcurrentLoad(session, config, values, generations, keyLocks, directory)

        session.storages.forEach { it.flush() }
        verifyAll(session, config, values, present)
        report("Load round #$round before deletion (${config.deletePercent}%)", directory, null, session)
        val deletedEntries = deleteEntries(session, config, present, round)
        verifyAll(session, config, values, present)
        report(
          "Load round #$round after  deletion", directory,
          "deletedEntries=$deletedEntries retainedEntries=${totalEntries - deletedEntries}", session,
        )
        closeStartedNs = System.nanoTime()
      }
      report("Load round #$round closed", directory, "close=${millis(System.nanoTime() - closeStartedNs)} ms")
    }

    repeat(config.steadyStateRoundsCount) { roundIndex ->
      val round = roundIndex + 1
      val openStartedNs = System.nanoTime()
      var closeStartedNs = 0L
      openSession(directory, extensions, config.invertedMapImplementation).use { session ->
        val openMillis = millis(System.nanoTime() - openStartedNs)
        verifyAll(session, config, values, present)
        report("Steady-state round #$round opened", directory, "open=$openMillis ms verifiedKeys=$totalEntries", session)
        closeStartedNs = System.nanoTime()
      }
      report("Steady-state round #$round closed", directory, "close=${millis(System.nanoTime() - closeStartedNs)} ms")
    }
    report("Load finished", directory, null)
  }

  /** Enables database DEBUG records in the standard test log for this test only. */
  private fun enableDatabaseDebugLogging(disposable: Disposable) {
    val julLogger = java.util.logging.Logger.getLogger(DATABASE_LOG_CATEGORY)
    val previousLevel = julLogger.level
    julLogger.level = Level.FINE
    Disposer.register(disposable) { julLogger.level = previousLevel }
  }

  private fun initialize(
    session: Session,
    config: LoadConfig,
    values: Array<IntArray>,
    present: Array<BooleanArray>,
  ): Long {
    var existingEntries = 0L
    for (index in session.storages.indices) {
      val storage = session.storages[index]
      for (key in 1..config.keysPerIndex) {
        val existingValue = readValue(storage, index, key)
        val value = existingValue ?: valueFor(index, key, 0).also { storage.addValue(key, key, it) }
        if (existingValue != null) existingEntries++
        values[index][key] = value
        present[index][key] = true
      }
      storage.flush()
    }
    return existingEntries
  }

  private fun restoreMissing(
    session: Session,
    config: LoadConfig,
    values: Array<IntArray>,
    present: Array<BooleanArray>,
  ): Long {
    var restoredEntries = 0L
    for (index in session.storages.indices) {
      val storage = session.storages[index]
      for (key in 1..config.keysPerIndex) {
        if (!present[index][key]) {
          storage.addValue(key, key, values[index][key])
          present[index][key] = true
          restoredEntries++
        }
      }
      storage.flush()
    }
    return restoredEntries
  }

  private fun deleteEntries(
    session: Session,
    config: LoadConfig,
    present: Array<BooleanArray>,
    round: Int,
  ): Long {
    val random = Random(config.seed xor (round.toLong() shl 32))
    var deletedEntries = 0L
    for (index in session.storages.indices) {
      val storage = session.storages[index]
      for (key in 1..config.keysPerIndex) {
        if (random.nextInt(100) < config.deletePercent) {
          storage.removeAllValues(key, key)
          present[index][key] = false
          deletedEntries++
        }
      }
      storage.flush()
    }
    return deletedEntries
  }

  private fun runConcurrentLoad(
    session: Session,
    config: LoadConfig,
    values: Array<IntArray>,
    generations: Array<IntArray>,
    keyLocks: Array<Array<Any>>,
    directory: Path,
  ) {
    val running = AtomicBoolean(true)
    val failure = AtomicReference<Throwable?>()
    val start = CountDownLatch(1)
    val flushGate = ReentrantReadWriteLock()
    val reads = LongAdder()
    val writes = LongAdder()
    val maximumReadNanos = AtomicLong()
    val maximumWriteNanos = AtomicLong()
    val maximumFlushNanos = AtomicLong()
    val executor = Executors.newFixedThreadPool(config.writerCount + config.readerCount)
    var lastReport: Long

    try {
      repeat(config.writerCount) { writer ->
        executor.submit {
          runWorker(running, failure, start) {
            val random = Random(config.seed + writer)
            val ownedIndexes = session.storages.indices.filter { it % config.writerCount == writer }
            while (running.get()) {
              val index = ownedIndexes[random.nextInt(ownedIndexes.size)]
              val key = randomKey(random, config.keysPerIndex)
              val started = System.nanoTime()
              flushGate.readLock().lock()
              try {
                synchronized(keyLocks[index][key % LOCK_STRIPES]) {
                  val nextGeneration = generations[index][key] + 1
                  val nextValue = valueFor(index, key, nextGeneration)
                  val storage = session.storages[index]
                  storage.removeAllValues(key, key)
                  storage.addValue(key, key, nextValue)
                  generations[index][key] = nextGeneration
                  values[index][key] = nextValue
                }
              }
              finally {
                flushGate.readLock().unlock()
              }
              maximumWriteNanos.accumulateAndGet(System.nanoTime() - started, ::maxOf)
              writes.increment()
            }
          }
        }
      }

      repeat(config.readerCount) { reader ->
        executor.submit {
          runWorker(running, failure, start) {
            val random = Random(config.seed xor (reader.toLong() shl 32))
            while (running.get()) {
              val index = random.nextInt(config.indexCount)
              val key = randomKey(random, config.keysPerIndex)
              val started = System.nanoTime()
              synchronized(keyLocks[index][key % LOCK_STRIPES]) {
                verifyValue(session.storages[index], index, key, values[index][key])
              }
              maximumReadNanos.accumulateAndGet(System.nanoTime() - started, ::maxOf)
              reads.increment()
            }
          }
        }
      }

      val started = System.nanoTime()
      val deadline = started + SECONDS.toNanos(config.durationSeconds.toLong())
      lastReport = started
      var nextReport = started + SECONDS.toNanos(config.reportSeconds.toLong())
      var nextFlush = started + SECONDS.toNanos(config.flushSeconds.toLong())
      start.countDown()
      while (running.get() && System.nanoTime() < deadline) {
        val nextEvent = minOf(deadline, nextReport, nextFlush)
        NANOSECONDS.sleep(minOf(SECONDS.toNanos(1), maxOf(1L, nextEvent - System.nanoTime())))
        var now = System.nanoTime()
        if (running.get() && now >= nextFlush) {
          val flushStarted = System.nanoTime()
          flushGate.writeLock().lock()
          try {
            session.storages.forEach { it.flush() }
          }
          finally {
            flushGate.writeLock().unlock()
          }
          maximumFlushNanos.accumulateAndGet(System.nanoTime() - flushStarted, ::maxOf)
          now = System.nanoTime()
          nextFlush = now + SECONDS.toNanos(config.flushSeconds.toLong())
        }
        if (now >= nextReport) {
          val intervalNanos = now - lastReport
          val intervalReads = reads.sumThenReset()
          val intervalWrites = writes.sumThenReset()
          report(
            "\trunning", directory,
            "reads=$intervalReads writes=$intervalWrites " +
            "readsPerSecond=${rate(intervalReads, intervalNanos)} writesPerSecond=${rate(intervalWrites, intervalNanos)} " +
            "maxReadMs=${millis(maximumReadNanos.getAndSet(0))} " +
            "maxWriteMs=${millis(maximumWriteNanos.getAndSet(0))} " +
            "maxFlushMs=${millis(maximumFlushNanos.getAndSet(0))}",
            session,
          )
          lastReport = now
          nextReport = now + SECONDS.toNanos(config.reportSeconds.toLong())
        }
      }
    }
    finally {
      running.set(false)
      start.countDown()
      executor.shutdown()
      if (!executor.awaitTermination(2, TimeUnit.MINUTES)) {
        executor.shutdownNow()
        check(executor.awaitTermination(10, SECONDS)) { "Load workers did not stop" }
      }
    }

    failure.get()?.let { throw AssertionError("Load worker failed; seed=${config.seed}", it) }
    val intervalNanos = System.nanoTime() - lastReport
    val intervalReads = reads.sumThenReset()
    val intervalWrites = writes.sumThenReset()
    report(
      "Load complete", directory,
      "reads=$intervalReads writes=$intervalWrites " +
      "readsPerSecond=${rate(intervalReads, intervalNanos)} writesPerSecond=${rate(intervalWrites, intervalNanos)} " +
      "maxReadMs=${millis(maximumReadNanos.getAndSet(0))} " +
      "maxWriteMs=${millis(maximumWriteNanos.getAndSet(0))} " +
      "maxFlushMs=${millis(maximumFlushNanos.getAndSet(0))}",
      session,
    )
  }

  private fun runWorker(
    running: AtomicBoolean,
    failure: AtomicReference<Throwable?>,
    start: CountDownLatch,
    body: () -> Unit,
  ) {
    try {
      start.await()
      body()
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      failure.compareAndSet(null, e)
      running.set(false)
    }
  }

  private fun verifyAll(
    session: Session,
    config: LoadConfig,
    values: Array<IntArray>,
    present: Array<BooleanArray>,
  ) {
    for (index in session.storages.indices) {
      for (key in 1..config.keysPerIndex) {
        val expected = if (present[index][key]) values[index][key] else null
        verifyValue(session.storages[index], index, key, expected)
      }
    }
  }

  private fun verifyValue(storage: IndexStorage<Int, Int>, index: Int, key: Int, expected: Int?) {
    val actual = readValue(storage, index, key)
    check(actual == expected) { "Wrong value: index=$index key=$key expected=$expected actual=$actual" }
  }

  private fun readValue(storage: IndexStorage<Int, Int>, index: Int, key: Int): Int? {
    var result: Int? = null
    storage.read<RuntimeException>(key) { container ->
      container.forEach { inputId, value ->
        check(inputId == key) { "Wrong input ID: index=$index key=$key inputId=$inputId" }
        check(result == null) { "Wrong entry count: index=$index key=$key expected=1 actual=2 or more" }
        result = value
        true
      }
      false
    }
    return result
  }

  private fun openSession(
    directory: Path,
    extensions: List<LoadIndexExtension>,
    invertedMapImplementation: DurableMapImplementation,
  ): Session {
    val provider = DurableDatabaseIndexLayoutProvider(
      databasePath = directory,
      forwardMapImplementation = KEY_VALUE_SEPARATELY,
      invertedMapImplementation = invertedMapImplementation,
    )
    val storages = ArrayList<IndexStorage<Int, Int>>(extensions.size)
    try {
      for (extension in extensions) {
        val indexStorageLayout = provider.getLayout(extension, emptyList())
        val indexStorage = indexStorageLayout.openIndexStorage()
        storages.add(indexStorage)
      }
      return Session(provider, storages)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      try {
        Session(provider, storages).close()
      }
      catch (closeError: Throwable) {
        rethrowControlFlowException(closeError)
        e.addSuppressed(closeError)
      }
      throw e
    }
  }

  private fun prepareDirectory(directory: Path): Path {
    val path = directory.toAbsolutePath().normalize()
    val indexRoot = PathManager.getIndexRoot().toAbsolutePath().normalize()
    require(!path.startsWith(indexRoot)) { "Choose a directory outside the IDE index root: $path" }
    if (!Files.exists(path)) {
      Files.createDirectory(path)
    }
    return path
  }

  private fun report(phase: String, directory: Path, details: String?, session: Session? = null) {
    var chunkFiles = 0
    var chunkFilesBytes = 0L
    Files.list(directory).use { files ->
      files.forEach { file ->
        if (Files.isRegularFile(file)) {
          try {
            chunkFilesBytes += Files.size(file)
            if (file.fileName.toString().startsWith("chunk-") && file.fileName.toString().endsWith(".dat")) {
              chunkFiles++
            }
          }
          catch (_: NoSuchFileException) {
            // Housekeeping can remove a chunk after the directory listing.
          }
        }
      }
    }
    val startedAtNs = System.nanoTime()
    val databaseDetails = session?.provider?.databaseMetrics()?.let(::formatDatabaseMetrics)
    val reportDetails = listOfNotNull(details, databaseDetails).joinToString(" ")
    println("[${System.currentTimeMillis()}] $phase: $chunkFiles chunk files (=${chunkFilesBytes / (1024 * 1024)} Mb total); " +
            "$reportDetails; " +
            "metrics took ${System.nanoTime() - startedAtNs} ns")
  }

  private fun formatDatabaseMetrics(metrics: DatabaseMetrics): String {
    val catalog = metrics.catalog()
    val chunks = metrics.chunks()
    val blocks = metrics.blocks()
    val lifecycle = metrics.lifecycleSummary()
    val recovery = metrics.recoverySummary()
    return buildString {
      append("Database{stores: ${catalog.storesTotal()}, ")
      append("chunks: ${chunks.total()}(${chunks.activeCurrent()}/${chunks.sealedCurrent()}/${chunks.retiredCurrent()}) ")
      append("mapped: ${chunks.totalMappedBytes() / (1024 * 1024)}Mb, ")
      append("blocks: ${blocks.total()}(${blocks.activeCurrent()}/${blocks.sealedCurrent()}/${blocks.retiredCurrent()})")
      if (lifecycle.isNotEmpty()) {
        append("; lifecycle={$lifecycle}")
      }
      if (recovery.isNotEmpty()) {
        append("; recovery={$recovery}")
      }
      append('}')
    }
  }

  private fun randomKey(random: Random, keysPerIndex: Int): Int {
    val hotKeys = maxOf(1, keysPerIndex / 5)
    return if (random.nextInt(100) < 80) random.nextInt(hotKeys) + 1 else random.nextInt(keysPerIndex) + 1
  }

  private fun valueFor(index: Int, key: Int, generation: Int): Int =
    (index + 1) * 73856093 xor key * 19349663 xor generation * 83492791

  private fun millis(nanos: Long): Long = NANOSECONDS.toMillis(nanos)

  private fun rate(count: Long, elapsedNanos: Long): Long = count * 1_000_000_000L / maxOf(1L, elapsedNanos)

  private class Session(
    val provider: DurableDatabaseIndexLayoutProvider,
    val storages: List<IndexStorage<Int, Int>>,
  ) : AutoCloseable {
    override fun close() {
      var failure: Throwable? = null
      for (storage in storages.asReversed()) {
        try {
          storage.close()
        }
        catch (e: Throwable) {
          rethrowControlFlowException(e)
          if (failure == null) failure = e else failure.addSuppressed(e)
        }
      }
      try {
        provider.close()
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        if (failure == null) failure = e else failure.addSuppressed(e)
      }
      failure?.let { throw it }
    }
  }

  private class LoadIndexExtension(index: Int) : FileBasedIndexExtension<Int, Int>() {
    private val indexId = ID.create<Int, Int>("IJPL255591LoadIndex$index")

    override fun getName(): ID<Int, Int> = indexId

    override fun getVersion(): Int = 1

    override fun getCacheSize(): Int = 1024

    override fun dependsOnFileContent(): Boolean = true

    override fun getKeyDescriptor(): KeyDescriptor<Int> = EnumeratorIntegerDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<Int> = EnumeratorIntegerDescriptor.INSTANCE

    override fun getIndexer(): DataIndexer<Int, Int, FileContent> =
      throw UnsupportedOperationException("The load test does not index file content")

    override fun getInputFilter(): FileBasedIndex.InputFilter =
      throw UnsupportedOperationException("The load test does not inspect files")
  }
}
