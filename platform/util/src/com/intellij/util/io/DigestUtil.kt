// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.nio.file.Path
import java.security.MessageDigest
import java.security.Provider
import java.security.SecureRandom
import kotlin.io.path.inputStream

@Suppress("FunctionName")
object DigestUtil {
  @JvmStatic
  val random: SecureRandom by lazy { SecureRandom() }

  // http://stackoverflow.com/a/41156 - shorter than UUID, but secure
  @JvmStatic
  fun randomToken(): String = BigInteger(130, random).toString(32)

  @JvmStatic
  fun md5(): MessageDigest = cloneDigest(md5)
  private val md5 by lazy(LazyThreadSafetyMode.PUBLICATION) { getMessageDigest("MD5") }

  @JvmStatic
  fun sha1(): MessageDigest = cloneDigest(sha1)
  private val sha1 by lazy(LazyThreadSafetyMode.PUBLICATION) { getMessageDigest("SHA-1") }

  @JvmStatic
  fun sha256(): MessageDigest = cloneDigest(sha256)
  private val sha256 by lazy(LazyThreadSafetyMode.PUBLICATION) { getMessageDigest("SHA-256") }

  fun sha3_224(): MessageDigest = cloneDigest(sha3_224)
  fun sha3_512(): MessageDigest = cloneDigest(sha3_512)

  @JvmStatic
  fun sha512(): MessageDigest = cloneDigest(sha512)
  private val sha512 by lazy(LazyThreadSafetyMode.PUBLICATION) { getMessageDigest("SHA-512") }

  @JvmStatic
  fun digestToHash(digest: MessageDigest) = bytesToHex(digest.digest())

  @JvmStatic
  fun sha256Hex(input: ByteArray): String = bytesToHex(sha256().digest(input))

  @JvmStatic
  fun sha256Hex(file: Path): String {
    try {
      val digest = sha256()
      val buffer = ByteArray(512 * 1024)
      file.inputStream().use {
        updateContentHash(digest, it, buffer)
      }
      return bytesToHex(digest.digest())
    }
    catch (e: IOException) {
      throw RuntimeException("Failed to read $file. ${e.message}", e)
    }
  }

  @JvmStatic
  fun sha1Hex(input: ByteArray): String = bytesToHex(sha1().digest(input))

  @JvmStatic
  fun md5Hex(input: ByteArray): String = bytesToHex(md5().digest(input))

  @JvmStatic
  @JvmOverloads
  fun updateContentHash(digest: MessageDigest, file: Path, buffer: ByteArray = ByteArray(512 * 1024)) {
    try {
      file.inputStream().use {
        updateContentHash(digest, it, buffer)
      }
    }
    catch (e: IOException) {
      throw RuntimeException("Failed to read $file. ${e.message}", e)
    }
  }

  @JvmStatic
  @JvmOverloads
  fun updateContentHash(digest: MessageDigest, inputStream: InputStream, buffer: ByteArray = ByteArray(512 * 1024)) {
    try {
      while (true) {
        val sz = inputStream.read(buffer)
        if (sz <= 0) {
          break
        }
        digest.update(buffer, 0, sz)
      }
    }
    catch (e: IOException) {
      throw RuntimeException("Failed to read stream. ${e.message}", e)
    }
  }
}

private val sunSecurityProvider: Provider = java.security.Security.getProvider("SUN")

private val sha3_224: MessageDigest by lazy(LazyThreadSafetyMode.PUBLICATION) { getMessageDigest("SHA3-224") }
private val sha3_512: MessageDigest by lazy(LazyThreadSafetyMode.PUBLICATION) { getMessageDigest("SHA3-512") }

private fun getMessageDigest(algorithm: String): MessageDigest {
  return MessageDigest.getInstance(algorithm, sunSecurityProvider)
}

/**
 * Digest cloning is faster than requesting a new one from [MessageDigest.getInstance].
 * This approach is used in Guava as well.
 */
private fun cloneDigest(digest: MessageDigest): MessageDigest {
  try {
    return digest.clone() as MessageDigest
  }
  catch (e: CloneNotSupportedException) {
    throw IllegalArgumentException("Message digest is not cloneable: $digest")
  }
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

private val HEX_ARRAY = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')