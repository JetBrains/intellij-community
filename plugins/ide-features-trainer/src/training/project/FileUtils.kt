// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.project

import com.intellij.UtilBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import org.apache.commons.lang.StringUtils
import java.io.*
import java.net.JarURLConnection
import java.net.URL

object FileUtils {
  private val LOG = Logger.getInstance(FileUtils::class.java)

  @Throws(IOException::class)
  fun copyJarResourcesRecursively(destDir: File,
                                  jarConnection: JarURLConnection,
                                  destinationFilter: FileFilter? = null): Boolean {
    val jarFile = jarConnection.jarFile

    val entries = jarFile.entries()
    while (entries.hasMoreElements()) {
      val entry = entries.nextElement()
      if (entry.name.startsWith(jarConnection.entryName)) {
        val filename = StringUtils.removeStart(entry.name, jarConnection.entryName)

        val f = File(destDir, filename)

        if (destinationFilter != null && !destinationFilter.accept(f)) continue

        if (!entry.isDirectory) {
          val entryInputStream = jarFile.getInputStream(entry)
          if (!copyStream(entryInputStream, f)) {
            return false
          }
          entryInputStream.close()
        }
        else {
          if (!ensureDirectoryExists(f)) {
            throw IOException("Could not create directory: " + f.absolutePath)
          }
        }
      }
    }
    return true
  }

  fun copyResourcesRecursively(originUrl: URL, destination: File, destinationFilter: FileFilter? = null): Boolean {
    try {
      val urlConnection = originUrl.openConnection()
      if (urlConnection is JarURLConnection)
        copyJarResourcesRecursively(destination, urlConnection, destinationFilter)
      else
        copyDirWithDestFilter(File(originUrl.path), destination, destinationFilter)
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

  fun ensureDirectoryExists(f: File): Boolean = f.exists() || f.mkdir()
}