// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.project

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
                                  jarConnection: JarURLConnection): Boolean {
    val jarFile = jarConnection.jarFile

    val entries = jarFile.entries()
    while (entries.hasMoreElements()) {
      val entry = entries.nextElement()
      if (entry.name.startsWith(jarConnection.entryName)) {
        val filename = StringUtils.removeStart(entry.name, jarConnection.entryName)

        val f = File(destDir, filename)
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

  fun copyResourcesRecursively(originUrl: URL, destination: File): Boolean {
    try {
      val urlConnection = originUrl.openConnection()
      if (urlConnection is JarURLConnection)
        copyJarResourcesRecursively(destination, urlConnection)
      else
        FileUtil.copyDirContent(File(originUrl.path), destination)
      return true
    }
    catch (e: IOException) {
      LOG.error(e)
    }
    return false
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