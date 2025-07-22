// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.objects

import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl

internal class Oid private constructor(private val bytes: ByteArray) {
  init {
    require(bytes.size == HASH_LENGTH) { "Expected 160-bit SHA1 hash, got ${bytes.size * 8} bits" }
  }

  companion object {
    const val HASH_LENGTH = 20

    fun fromByteArray(bytes: ByteArray): Oid {
      return Oid(bytes.copyOf())
    }

    fun fromHex(hexString: String): Oid {
      if (hexString.length != HASH_LENGTH * 2) {
        throw IllegalArgumentException("Expected 40-character hex string, got ${hexString.length}")
      }
      val bytes = ByteArray(HASH_LENGTH) { i ->
        hexString.substring(i * 2, i * 2 + 2).toInt(16).toByte()
      }
      return Oid(bytes)
    }

    fun fromHash(hash: Hash): Oid {
      return fromHex(hash.asString())
    }
  }

  fun hex(): String {
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun toByteArray(): ByteArray {
    return bytes.copyOf()
  }

  override fun toString(): String = hex()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Oid) return false
    return bytes.contentEquals(other.bytes)
  }

  override fun hashCode(): Int = bytes.contentHashCode()
}

internal fun Oid.toHash(): Hash {
  return HashImpl.build(hex())
}