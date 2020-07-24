package com.intellij.jps.cache.diffExperiment

import com.intellij.util.io.*
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

fun main(argv: Array<String>) {
  val commitHash = argv[0]
  val baseUrl = argv[1]
  val zip = File(argv[2]).apply { assert(exists()) { "$this doesn't exit" } }
  val currentHashesFile = File(argv[3])

  println("commit: $commitHash, zip: $zip(${Measures.toHumanSize(zip.length())}) url $baseUrl")

  val cachesFolder = createTempDir()
  // Unzip
  measure("Extract to $cachesFolder") {
    Decompressor.Zip(zip).extract(cachesFolder)
  }
  val tools = JpsCacheTools(60)

  //Get current state
  val currentState = measure("Get state for folder $cachesFolder") {
    tools.getStateByFolder(cachesFolder.toPath())
  }
  val totalSize = currentState.map { it.size }.sum()
  println("${currentState.size} files of size ${Measures.toHumanSize(totalSize)}")

  // Save current hashes and upload it
  currentHashesFile.writeText(tools.getJsonByState(currentState))
  measure("Upload $currentHashesFile (size ${Measures.toHumanSize(currentHashesFile.length())} to $baseUrl") {
    uploadJson(tools, currentHashesFile, baseUrl, commitHash)
  }

  //Upload caches
  val success = measure("Upload all caches") {
    uploadAllFiles(currentState, tools, baseUrl, cachesFolder)
  }
  assert(success) { "Failed to finish uploading within ${tools.secondsToWaitForFuture} seconds. Please, increase it." }
  exitProcess(0)
}

private fun uploadAllFiles(currentState: List<JpsCacheFileInfo>,
                           tools: JpsCacheTools,
                           baseUrl: String,
                           cachesFolder: File): Boolean {
  val count = AtomicInteger(currentState.size)
  val statusReporter: (Int) -> Unit = { completed ->
    println("$completed%")
  }
  println("Uploading files, completed: 0%")
  val finished = tools.withExecutor(statusReporter) { executor ->
    currentState.forEach { fileInfo ->
      executor.execute {
        val file = File(cachesFolder, fileInfo.path)
        assert(file.exists()) { "can't find $file" }
        HttpRequests.put(fileInfo.getUrl(baseUrl), null).write(fileInfo.forUploading(cachesFolder))
        count.decrementAndGet()
      }
    }
  }
  println("${count.get()} left")
  return finished
}


private fun <T> measure(title: String, block: () -> T): T {
  println("START: $title")
  val before = System.currentTimeMillis()
  val result: T = block()
  val seconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - before)
  println("END: $title ($seconds seconds)")
  return result
}

private fun uploadJson(tools: JpsCacheTools,
                       currentState: File,
                       baseUrl: String,
                       commitHash: String) {
  val manifestFileZipped = Files.createTempFile("manifest", "json.zip")
  ZipUtil.compressFile(currentState, manifestFileZipped.toFile())
  HttpRequests.put(tools.getZippedManifestUrl(baseUrl, commitHash), null).write(manifestFileZipped.readBytes())
  manifestFileZipped.delete()
}

enum class Measures {
  B, KB, MB, GB;

  private val size = BigDecimal.valueOf(1024L).pow(ordinal)

  companion object {
    fun toHumanSize(value: Long): String {
      val decValue = value.toBigDecimal()
      val measure = values().reversed().find { it.size < decValue } ?: B
      return "${decValue.divide(measure.size, measure.ordinal, RoundingMode.UP)} $measure"

    }
  }
}
