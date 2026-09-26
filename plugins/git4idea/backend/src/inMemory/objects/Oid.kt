// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.objects

import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl

/**
 * Represents a Git object id.
 */
internal class Oid private constructor(private val bytes: ByteArray) {
  companion object {
    fun fromByteArray(bytes: ByteArray): Oid {
      return Oid(bytes.copyOf())
    }

    fun fromHex(hexString: String): Oid {
      require(hexString.length % 2 == 0) { "Expected even-length hex object id, got ${hexString.length}" }

      val bytes = ByteArray(hexString.length / 2) { i ->
        hexString.substring(i * 2, i * 2 + 2).toInt(16).toByte()
      }
      return Oid(bytes)
    }

    fun fromHash(hash: Hash): Oid = fromHex(hash.asString())
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