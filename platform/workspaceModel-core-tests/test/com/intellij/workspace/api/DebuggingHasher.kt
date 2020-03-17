package com.intellij.workspace.api

import com.google.common.hash.Funnel
import com.google.common.hash.HashCode
import com.google.common.hash.Hasher
import com.intellij.openapi.util.text.StringUtil
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*

class DebuggingHasher: Hasher {
  private val sb = StringBuilder()

  override fun putByte(b: Byte): Hasher {
    sb.appendln("BYTE: $b")
    return this
  }

  override fun putDouble(d: Double): Hasher {
    sb.appendln("DOUBLE: $d")
    return this
  }

  override fun putLong(l: Long): Hasher {
    sb.appendln("LONG: $l")
    return this
  }

  override fun putInt(i: Int): Hasher {
    sb.appendln("INT: $i")
    return this
  }

  override fun putBytes(bytes: ByteArray): Hasher {
    sb.appendln("BYTES: ${StringUtil.toHexString(bytes)}")
    return this
  }

  override fun putBytes(bytes: ByteArray, off: Int, len: Int): Hasher {
    sb.appendln("BYTES RANGE: ${StringUtil.toHexString(Arrays.copyOfRange(bytes, off, off + len))}")
    return this
  }

  override fun putBytes(bytes: ByteBuffer): Hasher {
    sb.appendln("BYTES BUFFER: ${StringUtil.toHexString(bytes.array())}")
    return this
  }

  override fun putUnencodedChars(charSequence: CharSequence): Hasher {
    sb.appendln("CHARS: $charSequence")
    return this
  }

  override fun putBoolean(b: Boolean): Hasher {
    sb.appendln("BOOLEAN: $b")
    return this
  }

  override fun <T : Any?> putObject(instance: T, funnel: Funnel<in T>): Hasher = throw UnsupportedOperationException()

  override fun putShort(s: Short): Hasher {
    sb.appendln("SHORT: $s")
    return this
  }

  override fun putChar(c: Char): Hasher {
    sb.appendln("CHAR: $c")
    return this
  }

  override fun putFloat(f: Float): Hasher {
    sb.appendln("FLOAT: $f")
    return this
  }

  override fun hash(): HashCode = HashCode.fromBytes(sb.toString().toByteArray())

  override fun putString(charSequence: CharSequence, charset: Charset): Hasher {
    sb.appendln("STRING: $charSequence as ${charset.name()}")
    return this
  }
}