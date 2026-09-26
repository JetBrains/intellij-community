// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.delete
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.name
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

object StressTestUtil {

  class StressReportImpl : StressTestReport {
    override val okCount: Long get() = data.filterKeys { it.isOk }.map { it.value.first }.sum()
    override val failCount: Long get() = data.filterKeys { !it.isOk }.map { it.value.first }.sum()
    override val categoryCounts: Map<InteractionCategory, Long>
      get() = data.toMap().mapValues { it.value.first }
    override val categoryDetails: Map<InteractionCategory, List<String>>
      get() = data.toMap().mapValues { it.value.second }

    private val data = ConcurrentHashMap<InteractionCategory, Pair<Long, List<String>>>()

    fun addInteractionResult(result: InteractionResult) {
      data.compute(result.category) { _, before ->
        val detail = result.details?.let { listOf(it) } ?: emptyList()
        if (before == null) 1L to detail.take(MAX_DETAILS)
        else (before.first + 1) to (before.second + detail).take(MAX_DETAILS)
      }
    }

    override fun toString(): String {
      val totalCount = okCount + failCount
      val countToDetails = data.map { e ->
        val (cat, value) = e.toPair()
        val count = value.first
        val countFmt = String.format("%5d (%6.2f%%)", count, count * 100.0 / totalCount)
        val details = value.second
        count to
          "$countFmt ${if (cat.isOk) " ok " else "fail"} - ${cat.category}\n" +
          details.joinToString("") { "       - $it\n" }
      }.sortedBy { -it.first }
      return "Stress test report:\n" + countToDetails.joinToString("") { it.second }
    }
  }

  object AppRunner {
    @JvmStatic
    fun main(args: Array<String>) {
      check(args.size == 1) { args.contentToString() }

      @Suppress("UNCHECKED_CAST")
      val appClass = javaClass.classLoader.loadClass(args[0]).kotlin.also {
        require(it.java.interfaces.contains(App::class.java)) { "${it.qualifiedName} class does not implement App (see Domain.kt)" }
      } as KClass<out App>

      val appAgent = object : AppAgent {
        override val input: InputStream
          get() = System.`in`
        override val output: OutputStream
          get() = System.out
      }
      try {
        appClass.createInstance().run(appAgent)
      }
      catch (e: Throwable) {
        System.err.println("Error while running App: ${e.message} -> app exiting")
        e.printStackTrace()
      }
    }

    private val currentProcessInfo by lazy {
      ProcessHandle.current().info()
    }

    fun buildRunnerCommand(appClass: KClass<*>): List<String> {
      var adjustedArgs = currentProcessInfo.arguments().get().toList()
        .filterNot{ it.startsWith("-agentlib:jdwp=") }//remove debugger conf -- can't start 2 debugs with same conf
        .filterNot{ it.startsWith("-javaagent:") && it.endsWith("capture.props") }    //remove debugger support agent: not working without debug
      val testUtilArg = adjustedArgs.indexOf(StressTestUtil::class.java.name)
      check(testUtilArg >= 0) { currentProcessInfo }
      adjustedArgs = listOf(currentProcessInfo.command().get()) +
                     adjustedArgs.subList(0, testUtilArg) +
                     listOf(AppRunner.javaClass.name, appClass.qualifiedName)
      return adjustedArgs.toList()
    }
  }

  private class AppControllerImpl(override val workDir: Path, appClass: KClass<*>) : AppController {
    val runnerProcess: Process
    val runnerOutput: InputStream
    val runnerInput: OutputStream

    init {
      val processBuilder = ProcessBuilder(AppRunner.buildRunnerCommand(appClass))
      processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
      processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE)
      processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE)
      processBuilder.directory(workDir.toFile())

      runnerProcess = processBuilder.start()
      try {
        runnerInput = runnerProcess.outputStream.buffered(BUFFER_SIZE)
        runnerOutput = runnerProcess.inputStream.buffered(BUFFER_SIZE)
      }
      catch (e: Throwable) {
        try {
          runnerProcess.destroyForcibly().waitFor()
        } catch (killE: Throwable) {
          e.addSuppressed(killE)
        }
        throw e
      }
    }

    override fun kill() {
      //runnerProcess.destroyForcibly().waitFor()
      Runtime.getRuntime().exec("kill -9 ${runnerProcess.pid()}")
    }

    override fun isAlive(): Boolean = runnerProcess.isAlive
    override fun exitCode(): Int = runnerProcess.exitValue()
    override val appInput: OutputStream get() = runnerInput
    override val appOutput: InputStream get() = runnerOutput

    override fun close() {
      listOf(appInput::close, appOutput::close).map(::runCatching) // omit exceptions
      if (runnerProcess.isAlive) {
        val exited = runnerProcess.waitFor(2_000, TimeUnit.MILLISECONDS)
        if (!exited) {
          val status = runnerProcess.destroyForcibly().waitFor()
          System.err.println("runner workdir=${workDir.name} with pid=${runnerProcess.pid()} was killed (status=$status)")
        }
      }
    }
  }

  private fun launchUserSession(stressTestReport: StressReportImpl,
                                iterationsPassed: AtomicLong,
                                id: Int,
                                userClass: KClass<out User>,
                                appClass: KClass<out App>,
                                iterations: Int): Thread {
    val tempDir = FileUtil.createTempDirectory("stress-test", id.toString()).toPath()
    return thread {
      try {
        val userAgent = UserAgentImpl(id, stressTestReport, appClass, tempDir)
        repeat(iterations) {
          tempDir.forEachDirectoryEntry { it.delete(true) }

          userClass.createInstance().run(userAgent)

          iterationsPassed.incrementAndGet()
        }
      }
      catch (e: Throwable) {
        System.err.println("User #$id failed: ${e.message}")
        e.printStackTrace()
      }
    }
  }

  private class UserAgentImpl(override val id: Int,
                              private val stressReport: StressReportImpl,
                              private val appClass: KClass<*>,
                              private val workDir: Path) : UserAgent {
    override val random: Random = Random(id * 239)

    override fun runApplication(body: (AppController) -> Unit) {
      AppControllerImpl(workDir, appClass).use {
        body(it)
      }
    }

    override fun addInteractionResult(result: InteractionResult) = stressReport.addInteractionResult(result)
  }

  fun runStressTest(userClass: KClass<out User>,
                    appClass: KClass<out App>,
                    threads: Int,
                    itersPerThread: Int): StressTestReport {
    val iterationsPassed = AtomicLong(0)
    val report = StressReportImpl()
    val statusJob = CoroutineScope(Dispatchers.IO).launch {
      val total = itersPerThread.toLong() * threads
      while (true) {
        delay(250)
        val passed = iterationsPassed.get()
        val ok = report.okCount
        val fail = report.failCount
        print("\r${String.format("%.2f", passed * 100.0 / total)}% -- ${ok} ok/${fail} fail")
      }
    }
    statusJob.invokeOnCompletion { println("\r100%") }
    (1..threads).map { id ->
      launchUserSession(report, iterationsPassed, id, userClass, appClass, itersPerThread)
    }.map {
      try {
        it.join()
      }
      catch (e: Throwable) {
        System.err.println("session failed: $e")
      }
    }
    statusJob.cancel()
    return report
  }

  @OptIn(ExperimentalTime::class)
  @Suppress("UNCHECKED_CAST")
  @JvmStatic
  fun main(args: Array<String>) {
    require(args.size == 4) { "Arguments: <user class> <app class> <threads> <iterations per thread>" }
    val user = javaClass.classLoader.loadClass(args[0]).kotlin.also {
      require(it.java.interfaces.contains(User::class.java)) { "${it.qualifiedName} class does not implement User (see Domain.kt)" }
    } as KClass<out User>
    val app = javaClass.classLoader.loadClass(args[1]).kotlin.also {
      require(it.java.interfaces.contains(App::class.java)) { "${it.qualifiedName} class does not implement App (see Domain.kt)" }
    } as KClass<out App>
    val threads = args[2].toInt().also { require(it > 0) }
    val iterations = args[3].toInt().also { require(it > 0) }

    val report: StressTestReport
    val duration = measureTime {
      report = runStressTest(user, app, threads, iterations)
    }

    val stressEnv = System.getenv()
      .filter { it.key.startsWith("stress.") }
      .map { "${it.key}=${it.value}" }.joinToString(";")

    println("""
      Config:
       - user: ${user.qualifiedName}
       - app: ${app.qualifiedName}
       - threads: $threads
       - iterations: $iterations
       - stress test env: ${stressEnv}
    """.trimIndent())
    println("Time elapsed: $duration")
    println(report)
  }

  private const val MAX_DETAILS = 10
  private const val BUFFER_SIZE = 2 * 1024 * 1024
}