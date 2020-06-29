// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.util.lazyPub
import java.io.IOException
import java.math.BigInteger
import java.nio.file.Path
import java.security.MessageDigest
import java.security.Provider
import java.security.SecureRandom

object DigestUtil {
  private val sunSecurityProvider: Provider = java.security.Security.getProvider("SUN")

  @JvmStatic
  val random: SecureRandom by lazy { SecureRandom() }

  // http://stackoverflow.com/a/41156 - shorter than UUID, but secure
  @JvmStatic
  fun randomToken(): String = BigInteger(130, random).toString(32)

  @JvmStatic
  fun md5(): MessageDigest = md5.cloneDigest()
  private val md5 by lazyPub { getMessageDigest("MD5") }

  @JvmStatic
  fun sha1(): MessageDigest = sha1.cloneDigest()
  private val sha1 by lazyPub { getMessageDigest("SHA-1") }

  @JvmStatic
  fun sha256(): MessageDigest = sha256.cloneDigest()
  private val sha256 by lazyPub { getMessageDigest("SHA-256") }

  @JvmStatic
  fun sha256Hex(input: ByteArray): String = bytesToHex(sha256().digest(input))

  @JvmStatic
  fun sha1Hex(input: ByteArray): String = bytesToHex(sha1().digest(input))

  @Deprecated(message = "Current implementation is very specific: it mixes length of the array to the hash." +
                        "In general, it is enough to hash only bytes of the array. " +
                        "Implement the hashing yourself. " +
                        "Also make sure that you create new or reset the MessageDigest")
  @JvmStatic
  fun calculateContentHash(digest: MessageDigest, bytes: ByteArray): ByteArray {
    // Preserve contract compatibility: make sure the digest is reset.
    val resetDigest = digest.cloneDigest()
    resetDigest.update(bytes.size.toString().toByteArray())
    resetDigest.update("\u0000".toByteArray())
    resetDigest.update(bytes)
    return resetDigest.digest()
  }

  /**
   * Digest cloning is faster than requesting a new one from [MessageDigest.getInstance].
   * This approach is used in Guava as well.
   */
  private fun MessageDigest.cloneDigest(): MessageDigest =
    try {
      clone() as MessageDigest
    }
    catch (e: CloneNotSupportedException) {
      throw IllegalArgumentException("Message digest is not cloneable: ${this}")
    }

  @JvmStatic
  fun updateContentHash(digest: MessageDigest, path: Path) {
    try {
      val buff = ByteArray(512 * 1024)
      path.inputStream().use { iz ->
        while (true) {
          val sz = iz.read(buff)
          if (sz <= 0) break
          digest.update(buff, 0, sz)
        }
      }
    }
    catch (e: IOException) {
      throw RuntimeException("Failed to read $path. ${e.message}", e)
    }
  }

  private fun getMessageDigest(algorithm: String): MessageDigest =
    MessageDigest.getInstance(algorithm, sunSecurityProvider)

}

private fun bytesToHex(data: ByteArray): String {
  val l = data.size
  val chars = CharArray(l shl 1)
  var i = 0
  var j = 0
  while (i < l) {
    val v = data[i].toInt()
    chars[j++] = HEX_ARRAY[0xF0 and v ushr 4]
    chars[j++] = HEX_ARRAY[0x0F and v]
    i++
  }
  return String(chars)
}

@Suppress("SpellCheckingInspection")
private val HEX_ARRAY = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')