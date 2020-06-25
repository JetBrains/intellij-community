// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.nio.file.Path
import java.security.MessageDigest
import java.security.Provider
import java.security.SecureRandom

object DigestUtil {
  private val sunSecurityProvider: Provider = java.security.Security.getProvider("SUN")

  @JvmStatic
  val random by lazy { SecureRandom() }

  // http://stackoverflow.com/a/41156 - shorter than UUID, but secure
  @JvmStatic
  fun randomToken(): String = BigInteger(130, random).toString(32)

  @JvmStatic
  fun md5() = getMessageDigest("MD5")

  @JvmStatic
  fun sha1() = getMessageDigest("SHA-1")

  @JvmStatic
  fun sha256() = getMessageDigest("SHA-256")

  @JvmStatic
  fun calculateContentHash(digest: MessageDigest, bytes: ByteArray): ByteArray = calculateContentHash(digest, bytes, 0, bytes.size)

  @JvmStatic
  fun calculateContentHash(digest: MessageDigest, bytes: ByteArray, offset: Int, length: Int): ByteArray {
    val cloned = cloneDigest(digest)
    cloned.update(length.toString().toByteArray())
    cloned.update("\u0000".toByteArray())
    cloned.update(bytes, offset, length)
    return cloned.digest()
  }

  @JvmStatic
  fun calculateMergedHash(digest: MessageDigest, hashArrays: Array<ByteArray>): ByteArray {
    val cloned = cloneDigest(digest)
    for (bytes in hashArrays) {
      cloned.update(bytes)
    }
    return cloned.digest()
  }

  @JvmStatic
  fun updateContentHash(digest: MessageDigest, path: Path) {
    updateContentHash(path.inputStream(), digest, path.toString())
  }

  @JvmStatic
  fun calculateContentHash(digest: MessageDigest, virtualFile: VirtualFile): ByteArray {
    val cloned = cloneDigest(digest)
    updateContentHash(virtualFile.inputStream, cloned, virtualFile.url)
    return cloned.digest()
  }

  private fun updateContentHash(inputStream: InputStream, digest: MessageDigest, presentablePathForError: String) {
    try {
      val buff = ByteArray(512 * 1024)
      inputStream.use { iz ->
        while (true) {
          val sz = iz.read(buff)
          if (sz <= 0) break
          digest.update(buff, 0, sz)
        }
      }
    }
    catch (e: IOException) {
      throw RuntimeException("Failed to read $presentablePathForError. ${e.message}", e)
    }
  }

  private fun getMessageDigest(algorithm: String): MessageDigest {
    return MessageDigest.getInstance(algorithm, sunSecurityProvider)
  }

  private fun cloneDigest(digest: MessageDigest): MessageDigest {
    try {
      val clone = digest.clone() as MessageDigest
      clone.reset()
      return clone
    }
    catch (e: CloneNotSupportedException) {
      throw IllegalArgumentException("Message digest is not cloneable: $digest")
    }
  }
}