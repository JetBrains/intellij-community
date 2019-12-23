// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import java.math.BigInteger
import java.security.MessageDigest
import java.security.Provider
import java.security.SecureRandom

object DigestUtil {
  private val sunSecurityProvider: Provider = java.security.Security.getProvider("SUN")

  val random by lazy { SecureRandom() }

  @JvmStatic
  fun md5() = getMessageDigest("MD5")

  @JvmStatic
  fun sha1() = getMessageDigest("SHA-1")

  @JvmStatic
  fun sha256() = getMessageDigest("SHA-256")

  @JvmStatic
  fun calculateContentHash(digest: MessageDigest, bytes: ByteArray): ByteArray =
    calculateContentHash(digest, bytes, 0, bytes.size)

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

  // http://stackoverflow.com/a/41156 - shorter than UUID, but secure
  fun randomToken(): String {
    return BigInteger(130, random).toString(32)
  }

  private fun getMessageDigest(algorithm: String): MessageDigest {
    return MessageDigest.getInstance(algorithm, sunSecurityProvider)
  }

  private fun cloneDigest(digest: MessageDigest): MessageDigest = try {
    val clone = digest.clone() as MessageDigest
    clone.reset()
    clone
  }
  catch (e: CloneNotSupportedException) {
    throw IllegalArgumentException("Message digest is not cloneable: $digest")
  }
}