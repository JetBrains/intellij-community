package com.intellij.jps.cache.diffExperiment


import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdInputStream
import com.google.common.collect.Maps
import com.google.common.hash.Hashing
import com.google.common.io.CountingInputStream
import com.google.common.io.Files
import com.google.gson.Gson
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.ceil
import java.nio.file.Files as NioFiles

data class JpsCacheFileInfo(val size: Long, val path: String, @Volatile var hash: Long = 0) {
  companion object {
    private const val minSizeToZip = 512

    private fun archive(uncompressed: ByteArray): ByteArray {
      return Zstd.compress(uncompressed)
    }
  }

  private val shouldArchive = size > minSizeToZip

  fun forUploading(srcFolder: File): ByteArray =
    File(srcFolder, path).readBytes().let { bytes -> if (shouldArchive) archive(bytes) else bytes }

  fun afterDownloading(destFolder: File, dataStream: InputStream): Long {
    val countingDataStream = CountingInputStream(dataStream)
    val dest = File(destFolder, path).apply {
      parentFile.mkdirs()
    }
    (if (shouldArchive) ZstdInputStream(countingDataStream) else countingDataStream).use { stream ->
      dest.writeBytes(stream.readBytes())
    }
    return countingDataStream.count
  }

  fun getUrl(baseUrl: String) = "$baseUrl/$path/$hash"
}

class JpsCacheTools(val secondsToWaitForFuture: Long) {
  companion object {
    private const val EXPECTED_NUMBER_OF_FILES = 60_000
    private val HASH_FUNCTION = Hashing.murmur3_128()
    private const val MANIFESTS_PATH = "manifests"
  }

  inner class ExecutionApi {
    private val futures = mutableListOf<Future<*>>()
    fun execute(code: () -> Unit) {
      futures.add(executor.submit(code))
    }

    fun waitForFutures(statusListener: ((completed: Int) -> Unit)? = null): Boolean {
      val step: Int = ceil(futures.size.toDouble() / 100).toInt()
      var completed = 0
      var percent = 0
      try {
        futures.forEach {
          it.get(secondsToWaitForFuture, TimeUnit.SECONDS)
          statusListener?.let {
            completed++
            val newPercent = completed / step
            if (newPercent != percent) {
              percent = newPercent
              it(newPercent)
            }
          }
        }
        return true
      }
      catch (_: TimeoutException) {
        return false
      }
    }
  }

  private val executor: ExecutorService

  init {
    val numOfThreads = Runtime.getRuntime().availableProcessors() - 1
    executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("JPS_Cache", numOfThreads)
  }


  fun getJsonByState(files: Collection<JpsCacheFileInfo>): String = Gson().toJson(ArrayList(files))
  fun getStateByJsonFile(jsonFilePath: Path): List<JpsCacheFileInfo> = getStateByReader(
    Files.newReader(jsonFilePath.toFile(), StandardCharsets.UTF_8))


  fun getZippedManifestUrl(baseUrl: String, commitHash: String) = "$baseUrl/$MANIFESTS_PATH/$commitHash"

  @JvmOverloads
  fun getStateByFolder(rootPath: Path,
                       existing: Map<String, JpsCacheFileInfo>? = null): List<JpsCacheFileInfo> {
    val rootFile = rootPath.toFile()
    val result: MutableList<JpsCacheFileInfo> = ArrayList(EXPECTED_NUMBER_OF_FILES)
    withExecutor { executor ->
      NioFiles.walkFileTree(rootPath, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
          val relativePath = FileUtil.getRelativePath(rootFile, file.toFile())!!.replace(File.separatorChar, '/')
          val fileSize = attrs.size()
          val fileInfo = JpsCacheFileInfo(fileSize, relativePath)
          result.add(fileInfo)
          existing?.get(relativePath)?.let { existingInfo ->
            if (existingInfo.size != fileSize) {
              return FileVisitResult.CONTINUE
            }
          }
          executor.execute { fileInfo.hash = getHash(file) }
          return FileVisitResult.CONTINUE
        }
      })
    }
    return result
  }

  fun withExecutor(statusListener: ((completed: Int) -> Unit)? = null, code: (executeApi: ExecutionApi) -> Unit): Boolean {
    val api = ExecutionApi()
    code(api)
    return api.waitForFutures(statusListener)
  }

  fun getFilesToUpdate(folder: Path,
                       toState: List<JpsCacheFileInfo>): List<JpsCacheFileInfo> {
    val toStateMap = mapFromList(toState)
    val currentState = getStateByFolder(folder, toStateMap)
    return getFilesToUpdate(currentState, toStateMap)
  }

  fun getFilesToUpdate(fromState: List<JpsCacheFileInfo>,
                       toState: List<JpsCacheFileInfo>): List<JpsCacheFileInfo> {
    return getFilesToUpdate(fromState, mapFromList(toState))
  }

  private fun getFilesToUpdate(fromState: List<JpsCacheFileInfo>,
                               toState: Map<String, JpsCacheFileInfo>): List<JpsCacheFileInfo> =
    Maps.difference(mapFromList(fromState), toState).let { difference ->
      difference.entriesOnlyOnRight().map { it.value } + difference.entriesDiffering().values.map { it.rightValue() }
    }

  private fun getHash(file: Path): Long = Files.asByteSource(file.toFile()).hash(HASH_FUNCTION).asLong()
  private fun mapFromList(list: List<JpsCacheFileInfo>): Map<String, JpsCacheFileInfo> = list.associateBy { it.path }

  private fun getStateByReader(readerWithJson: Reader): List<JpsCacheFileInfo> =
    Gson().fromJson(readerWithJson, Array<JpsCacheFileInfo>::class.java).toList()

}
