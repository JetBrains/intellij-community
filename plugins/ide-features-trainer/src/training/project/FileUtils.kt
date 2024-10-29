// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.UtilBundle
import com.intellij.util.io.URLUtil
import java.io.*
import java.net.URL
import java.util.jar.JarFile

object FileUtils {
  private val LOG = Logger.getInstance(FileUtils::class.java)

  @Throws(IOException::class)
  fun copyJarResourcesRecursively(destDir: File,
                                  jarPath: String,
                                  destinationFilter: FileFilter? = null): Boolean {
    val splitJarPath = splitJarPath(jarPath)
    val mayBeEscapedFile = URL(splitJarPath.first).file
    val file = URLUtil.unescapePercentSequences(mayBeEscapedFile)
    val jarFile = JarFile(file)
    val prefix = splitJarPath.second

    val entries = jarFile.entries()
    while (entries.hasMoreElements()) {
      val entry = entries.nextElement()
      if (entry.name.startsWith(prefix)) {
        val filename = StringUtil.trimStart(entry.name, prefix)

        val f = File(destDir, filename)

        if (destinationFilter != null && !destinationFilter.accept(f)) continue

        if (!entry.isDirectory) {
          if (!ensureDirectoryExists(f.parentFile)) {
            LOG.error("Cannot create directory: " + f.parentFile)
          }
          val entryInputStream = jarFile.getInputStream(entry)
          if (!copyStream(entryInputStream, f)) {
            return false
          }
          entryInputStream.close()
        }
      }
    }
    return true
  }

  fun copyResourcesRecursively(originUrl: URL, destination: File, destinationFilter: FileFilter? = null): Boolean {
    try {
      if (originUrl.protocol == URLUtil.JAR_PROTOCOL) {
        copyJarResourcesRecursively(destination, originUrl.file, destinationFilter)
      }
      else if (originUrl.protocol == URLUtil.FILE_PROTOCOL) {
        copyDirWithDestFilter(File(originUrl.path), destination, destinationFilter)
      }
      return true
    }
    catch (e: IOException) {
      LOG.error(e)
    }
    return false
  }

  // Copied from FileUtil#copyDir but with filter for destination instead of source
  private fun copyDirWithDestFilter(fromDir: File, toDir: File, destinationFilter: FileFilter?) {
    FileUtil.ensureExists(toDir)
    if (FileUtil.isAncestor(fromDir, toDir, true)) {
      LOG.error(fromDir.absolutePath + " is ancestor of " + toDir + ". Can't copy to itself.")
      return
    }
    val files = fromDir.listFiles() ?: throw IOException(UtilBundle.message("exception.directory.is.invalid", fromDir.path))
    if (!fromDir.canRead()) throw IOException(UtilBundle.message("exception.directory.is.not.readable", fromDir.path))
    for (file in files) {
      val destinationFile = File(toDir, file.name)
      if (file.isDirectory) {
        copyDirWithDestFilter(file, destinationFile, destinationFilter)
      }
      else {
        if (destinationFilter == null || destinationFilter.accept(destinationFile)) {
          FileUtil.copy(file, destinationFile)
        }
      }
    }
  }

  private fun copyStream(inputStream: InputStream, f: File): Boolean {
    try {
      return copyStream(inputStream, FileOutputStream(f))
    }
    catch (e: FileNotFoundException) {
      LOG.error(e)
    }
    return false
  }

  private fun copyStream(inputStream: InputStream, os: OutputStream): Boolean {
    try {
      val buf = ByteArray(1024)
      var len = inputStream.read(buf)
      while (len > 0) {
        os.write(buf, 0, len)
        len = inputStream.read(buf)
      }
      inputStream.close()
      os.close()
      return true
    }
    catch (e: IOException) {
      LOG.error(e)
    }
    return false
  }

  private fun ensureDirectoryExists(f: File): Boolean = f.exists() || f.mkdirs()

  private fun splitJarPath(path: String): Pair<String, String> {
    val lastIndexOf = path.lastIndexOf(".jar!/")
    if (lastIndexOf == -1) throw IOException("Invalid Jar path format")
    val splitIdx = lastIndexOf + 4 // ".jar"
    val filePath = path.substring(0, splitIdx)
    val pathInsideJar = path.substring(splitIdx + 2, path.length) // remove "!/"
    return Pair(filePath, pathInsideJar)
  }
}