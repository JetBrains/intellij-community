// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.reproducibleBuilds.diffTool

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Decompressor
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

class FileTreeContentComparison(private val diffDir: Path = Path.of(System.getProperty("user.dir")).resolve(".diff"),
                                private val tempDir: Path = Files.createTempDirectory(this::class.java.simpleName)) {
  private companion object {
    fun process(vararg command: String, workDir: Path? = null, ignoreExitCode: Boolean = false): ProcessCallResult {
      require(command.isNotEmpty())
      val process = ProcessBuilder(*command).directory(workDir?.toFile()).start()
      val output = process.inputStream.bufferedReader().use { it.readText() }
      if (!process.waitFor(10, TimeUnit.MINUTES)) {
        process.destroyForcibly().waitFor()
        error("${command.joinToString(separator = " ")} timed out")
      }
      require(ignoreExitCode || process.exitValue() == 0) { output }
      return ProcessCallResult(process.exitValue(), output)
    }

    class ProcessCallResult(val exitCode: Int, val stdOut: String)

    private fun isAvailable(vararg command: String): Boolean = try {
      process(*command, ignoreExitCode = true).exitCode == 0
    }
    catch (e: IOException) {
      System.err.println("${command.first()} is not available: ${e.message}")
      false
    }

    val isJavapAvailable by lazy { isAvailable("javap", "-version") }
    val isUnsquashfsAvailable by lazy { isAvailable("unsquashfs", "-help") }
    val isDiffoscopeAvailable by lazy { isAvailable("diffoscope", "--version") }
    val is7zAvailable by lazy { isAvailable("7z", "--help") }
    val isUnzipAvailable by lazy { isAvailable("unzip", "--help") }
    val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    @JvmStatic
    fun main(args: Array<String>) {
      require(args.count() == 2)
      val path1 = Path.of(args[0])
      val path2 = Path.of(args[1])
      require(path1.exists())
      require(path2.exists())
      val test = FileTreeContentComparison()
      val assertion = when {
        path1.isDirectory() && path2.isDirectory() -> test.assertTheSameDirectoryContent(path1, path2, deleteBothAfterwards = false).error
        path1.isRegularFile() && path2.isRegularFile() -> test.assertTheSameFile(path1, path2)
        else -> throw IllegalArgumentException()
      }
      if (assertion != null) throw assertion
      println("Done.")
    }
  }

  init {
    FileUtil.delete(diffDir)
    Files.createDirectories(diffDir)
  }

  private fun listingDiff(firstIteration: Set<Path>, nextIteration: Set<Path>) =
    ((firstIteration - nextIteration) + (nextIteration - firstIteration))

  private fun assertTheSameFile(relativeFilePath: Path, dir1: Path, dir2: Path): AssertionError? {
    val path1 = dir1.resolve(relativeFilePath)
    val path2 = dir2.resolve(relativeFilePath)
    return assertTheSameFile(path1, path2, "$relativeFilePath")
  }

  fun assertTheSameFile(path1: Path, path2: Path, relativeFilePath: String = path1.name): AssertionError? {
    if (!Files.exists(path1) ||
        !Files.exists(path2) ||
        !path1.isRegularFile() ||
        path1.checksum() == path2.checksum() &&
        path1.permissions() == path2.permissions()) {
      return null
    }
    println("Failed for $relativeFilePath")
    require(path1.extension == path2.extension)
    val contentError = when (path1.extension) {
      "tar.gz", "gz", "tar" -> assertTheSameDirectoryContent(
        path1.unpackingDir().also { Decompressor.Tar(path1).extract(it) },
        path2.unpackingDir().also { Decompressor.Tar(path2).extract(it) },
        deleteBothAfterwards = true
      ).error ?: AssertionError("No difference in $relativeFilePath content. Timestamp or ordering issue?")
      "zip", "jar", "ijx", "sit" -> assertTheSameDirectoryContent(
        path1.unpackingDir().also { Decompressor.Zip(path1).withZipExtensions().extract(it) },
        path2.unpackingDir().also { Decompressor.Zip(path2).withZipExtensions().extract(it) },
        deleteBothAfterwards = true
      ).error ?: run {
        saveDiff(relativeFilePath, path1, path2)
        AssertionError("No difference in $relativeFilePath content. Timestamp or ordering issue?")
      }
      "dmg" -> {
        println(".dmg cannot be built reproducibly, content comparison is required")
        if (SystemInfo.isMac) {
          path1.mountDmg { dmg1Content ->
            path2.mountDmg { dmg2Content ->
              assertTheSameDirectoryContent(dmg1Content, dmg2Content, deleteBothAfterwards = false).error
            }
          }
        }
        else {
          println("macOS is required to compare content of .dmg, skipping")
          null
        }
      }
      "snap" -> {
        println(".snap cannot be built reproducibly, content comparison is required")
        if (isUnsquashfsAvailable) {
          assertTheSameDirectoryContent(
            path1.unSquash(path1.unpackingDir()),
            path2.unSquash(path2.unpackingDir()),
            deleteBothAfterwards = true
          ).error
        }
        else {
          println("unsquashfs should be installed to compare content of .snap, skipping")
          null
        }
      }
      else -> if (path1.checksum() != path2.checksum()) {
        saveDiff(relativeFilePath, path1, path2)
        AssertionError("Checksum mismatch for $relativeFilePath")
      }
      else null
    }
    if (path1.permissions() != path2.permissions()) {
      val permError = AssertionError("Permissions mismatch for $relativeFilePath: ${path1.permissions()} vs ${path2.permissions()}")
      contentError?.addSuppressed(permError) ?: return permError
    }
    return contentError
  }

  private fun saveDiff(relativePath: String, file1: Path, file2: Path) {
    fun fileIn(subdir: String, path: String = relativePath) =
      diffDir.resolve(subdir).resolve(path).apply {
        parent.createDirectories()
      }
    val artifacts1 = fileIn("artifacts1")
    val artifacts2 = fileIn("artifacts2")
    file1.writeContent(artifacts1)
    file2.writeContent(artifacts2)
    fileIn("diff", relativePath.removeSuffix(".txt") + ".diff.txt")
      .writeText(diff(artifacts1, artifacts2))
  }

  private fun Path.checksum(): String = inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    DigestInputStream(input, digest).use {
      var bytesRead = 0
      val buffer = ByteArray(1024 * 8)
      while (bytesRead != -1) {
        bytesRead = it.read(buffer)
      }
    }
    Base64.getEncoder().encodeToString(digest.digest())
  }

  private fun Path.permissions(): Set<PosixFilePermission> =
    Files.getFileAttributeView(this, PosixFileAttributeView::class.java)
      ?.readAttributes()?.permissions() ?: emptySet()

  private fun Path.writeContent(target: Path) {
    val content = when (extension) {
      "tar.gz", "gz", "tar" -> error("$this is expected to be already unpacked")
      "jar", "zip", "ijx", "sit" -> if (isUnzipAvailable) process("unzip", "-l", "$this").stdOut else null
      "class" -> if (isJavapAvailable) process("javap", "-verbose", "$this").stdOut else null
      "dmg", "exe" -> if (is7zAvailable) process("7z", "l", "$this").stdOut else null
      "json", "manifest" -> gson.toJson(JsonParser.parseString(readText()))
      else -> null
    }
    if (content != null) {
      target.writeText(content)
    }
    else {
      copyTo(target, overwrite = true)
    }
  }

  private fun Path.unpackingDir(): Path {
    val unpackingDir = tempDir
      .resolve("unpacked")
      .resolve("$fileName".replace(".", "_"))
      .resolve(UUID.randomUUID().toString())
    FileUtil.delete(unpackingDir)
    Files.createDirectories(unpackingDir)
    return unpackingDir
  }

  private fun diff(path1: Path, path2: Path): String {
    return if (isDiffoscopeAvailable) {
      process("diffoscope", "$path1", "$path2",
              ignoreExitCode = true).stdOut
    }
    else {
      process("git", "diff", "--no-index", "--", "$path1", "$path2",
              ignoreExitCode = true).stdOut
    }
  }

  private fun <T> Path.mountDmg(action: (mountPoint: Path) -> T): T {
    require(SystemInfo.isMac)
    require(extension == "dmg")
    val mountPoint = Path.of("/Volumes/${UUID.randomUUID()}")
    require(!mountPoint.exists())
    process("hdiutil", "attach", "-mountpoint", "$mountPoint", "$this")
    return try {
      action(mountPoint)
    }
    finally {
      process("diskutil", "unmount", "$mountPoint")
    }
  }

  private fun Path.unSquash(target: Path): Path {
    process("unsquashfs", "$this", workDir = target)
    return target
  }

  private fun Path.listDirectory(): List<Path> {
    require(isDirectory()) {
      "$this is expected to be directory"
    }
    return Files.walk(this).use { paths ->
      paths.filter { it.name != ".DS_Store" }
        .filter { it.name != ".CacheDeleteDiscardedCaches" }
        .toList()
    }
  }

  fun assertTheSameDirectoryContent(dir1: Path, dir2: Path, deleteBothAfterwards: Boolean): ComparisonResult {
    val listing1 = dir1.listDirectory()
    val listing2 = dir2.listDirectory()
    val relativeListing1 = listing1.map(dir1::relativize)
    val listingDiff = listingDiff(relativeListing1.toSet(), listing2.map(dir2::relativize).toSet())
    val contentComparisonErrors = relativeListing1.mapNotNull { assertTheSameFile(it, dir1, dir2) }
    val error = when {
      listingDiff.isNotEmpty() -> AssertionError(listingDiff.joinToString(prefix = "Listing diff for $dir1 and $dir2:\n", separator = "\n"))
      contentComparisonErrors.isNotEmpty() -> AssertionError("$dir1 doesn't match $dir2")
      else -> null
    }?.apply {
      contentComparisonErrors.forEach(::addSuppressed)
    }
    val comparedFiles = listing1
      .filterNot { it.isDirectory() }
      .map(dir1::relativize)
      .minus(listingDiff.toSet())
    if (deleteBothAfterwards) {
      NioFiles.deleteRecursively(dir1)
      NioFiles.deleteRecursively(dir2)
    }
    return ComparisonResult(comparedFiles, error)
  }

  class ComparisonResult(val comparedFiles: List<Path>, val error: AssertionError?)
}
