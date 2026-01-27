// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.utils.io.createFile
import com.intellij.util.io.delete
import com.intellij.vcsUtil.VcsUtil
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

private val LOG = Logger.getInstance(Executor::class.java)

/**
 * After eliminating all usages of [Executor] singleton, this class can hold the current directory.
 */
class ExecutorContextImpl(initialCurrentDir: Path) : ExecutorContext {
  init {
      Executor.cd(initialCurrentDir)
  }
  override var ourCurrentDir: Path
    get() = Executor.ourCurrentDir()
    set(value) { Executor.cd(value) }
}

interface ExecutorContext {

  var ourCurrentDir: Path

  private fun cdAbs(absolutePath: String) {
    ourCurrentDir = Path(absolutePath)
    debug("# cd " + shortenPath(absolutePath))
  }

  fun debug(msg: String) {
    if (msg.isNotBlank()) {
      LOG.info(msg)
    }
  }

  private fun cdRel(relativePath: String) {
    cd(ourCurrentDir.resolve(relativePath))
  }

  fun cd(dir: Path) {
    cdAbs(dir.toAbsolutePath().normalize().toString())
  }

  fun cd(relativeOrAbsolutePath: String) {
    if (relativeOrAbsolutePath.startsWith("/") || relativeOrAbsolutePath.get(1) == ':') {
      cdAbs(relativeOrAbsolutePath)
    }
    else {
      cdRel(relativeOrAbsolutePath)
    }
  }

  fun cd(dir: VirtualFile) {
    cd(dir.getPath())
  }

  fun pwd(): String {
    return ourCurrentDir.pathString
  }

  fun touch(filePath: String): Path {
    try {
      val file = child(filePath)
      assert(!file.exists()) { "File " + file + " shouldn't exist yet" }
      file.parent.createDirectories() // ensure to create the directories
      file.createFile()
      debug("# touch " + filePath)
      return file
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  fun touch(fileName: String, content: String): Path {
    val filePath = touch(fileName)
    echo(fileName, content)
    return filePath
  }

  fun echo(fileName: String, content: String) {
    try {
      Files.write(child(fileName), content.toByteArray(StandardCharsets.UTF_8), StandardOpenOption.APPEND, StandardOpenOption.CREATE)
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  @Throws(IOException::class)
  fun overwrite(fileName: String, content: String) {
    overwrite(child(fileName), content)
  }

  @Throws(IOException::class)
  fun overwrite(file: Path, content: String) {
    Files.write(file, content.toByteArray(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
  }

  @Throws(IOException::class)
  fun append(file: Path, content: String) {
    Files.write(file, content.toByteArray(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
  }

  @Throws(IOException::class)
  fun append(fileName: String, content: String) {
    append(child(fileName), content)
  }

  fun rm(fileName: String) {
    rm(child(fileName))
  }

  fun rm(file: Path) {
    file.delete()
  }

  fun mkdir(dirName: String): Path {
    val file = child(dirName)
    Files.createDirectory(file)
    debug("# mkdir " + dirName)
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
    return file
  }

  fun cat(fileName: String): String {
    try {
      val content = child(fileName).readText()
      debug("# cat " + fileName)
      return content
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  fun cp(fileName: String, destinationDir: Path) {
    try {
      Files.copy(child(fileName), destinationDir.resolve(fileName))
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  fun splitCommandInParameters(command: String): MutableList<String?> {
    val split: MutableList<String?> = ArrayList<String?>()

    var insideParam = false
    var currentParam = StringBuilder()
    for (c in command.toCharArray()) {
      var flush = false
      if (insideParam) {
        if (c == '\'') {
          insideParam = false
          flush = true
        }
        else {
          currentParam.append(c)
        }
      }
      else if (c == '\'') {
        insideParam = true
      }
      else if (c == ' ') {
        flush = true
      }
      else {
        currentParam.append(c)
      }

      if (flush) {
        if (currentParam.toString().isNotBlank()) {
          split.add(currentParam.toString())
        }
        currentParam = StringBuilder()
      }
    }

    // last flush
    if (currentParam.toString().isNotBlank()) {
      split.add(currentParam.toString())
    }
    return split
  }


  private fun shortenPath(path: String): String {
    val split: Array<String?> = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    if (split.size > 3) {
      // split[0] is empty, because the path starts from /
      return String.format("/%s/.../%s/%s", split[1], split[split.size - 2], split[split.size - 1])
    }
    return path
  }

  fun child(fileName: String): Path {
    return ourCurrentDir.resolve(fileName)
  }

  fun childPath(fileName: String): FilePath {
    val child = child(fileName)
    return VcsUtil.getFilePath(child, child.isDirectory())
  }
}
