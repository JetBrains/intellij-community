/*
 * Copyright 2020 Adrien Grand and the lz4-java contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.intellij.util.io

import net.jpountz.lz4.LZ4Compressor
import net.jpountz.lz4.LZ4Exception
import net.jpountz.lz4.LZ4FastDecompressor
import net.jpountz.util.SafeUtils
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.invoke.MethodHandles
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

// see https://github.com/lz4/lz4-java/issues/183

/**
 * This file is forked from https://github.com/lz4/lz4-java. In particular, it forks the following file
 * net.jpountz.lz4.LZ4JavaSafeFastDecompressor.
 *
 * It modifies the original implementation to use custom LZ4SafeUtils and SafeUtils implementations which
 * include performance improvements.
 */
@Internal
object LZ4Decompressor : LZ4FastDecompressor() {
  @Suppress("DuplicatedCode")
  override fun decompress(src: ByteArray, srcOff: Int, dest: ByteArray, destOff: Int, destLen: Int): Int {
    SafeUtils.checkRange(src, srcOff)
    SafeUtils.checkRange(dest, destOff, destLen)
    if (destLen == 0) {
      if (SafeUtils.readByte(src, srcOff).toInt() != 0) {
        throw LZ4Exception("Malformed input at $srcOff")
      }
      else {
        return 1
      }
    }
    else {
      val destEnd = destOff + destLen
      var sOff = srcOff
      var dOff = destOff
      while (true) {
        val token = SafeUtils.readByte(src, sOff).toInt() and 255
        ++sOff
        var literalLen = token ushr 4
        if (literalLen == 15) {
          var len: Byte
          while (SafeUtils.readByte(src, sOff++).also { len = it }.toInt() == -1) {
            literalLen += 255
          }
          literalLen += len.toInt() and 255
        }
        val literalCopyEnd = dOff + literalLen
        if (literalCopyEnd > destEnd - 8) {
          return if (literalCopyEnd != destEnd) {
            throw LZ4Exception("Malformed input at $sOff")
          }
          else {
            System.arraycopy(src, sOff, dest, dOff, literalLen)
            sOff += literalLen
            sOff - srcOff
          }
        }
        wildArraycopy(src, sOff, dest, dOff, literalLen)
        sOff += literalLen
        val matchDec = SafeUtils.readShortLE(src, sOff)
        sOff += 2
        val matchOff = literalCopyEnd - matchDec
        if (matchOff < destOff) {
          throw LZ4Exception("Malformed input at $sOff")
        }
        var matchLen = token and 15
        if (matchLen == 15) {
          var len: Byte
          while (SafeUtils.readByte(src, sOff++).also { len = it }.toInt() == -1) {
            matchLen += 255
          }
          matchLen += len.toInt() and 255
        }
        matchLen += 4
        val matchCopyEnd = literalCopyEnd + matchLen
        if (matchCopyEnd > destEnd - 8) {
          if (matchCopyEnd > destEnd) {
            throw LZ4Exception("Malformed input at $sOff")
          }
          safeIncrementalCopy(dest, matchOff, literalCopyEnd, matchLen)
        }
        else {
          wildIncrementalCopy(dest, matchOff, literalCopyEnd, matchCopyEnd)
        }
        dOff = matchCopyEnd
      }
    }
  }

  override fun decompress(src: ByteBuffer, srcOff: Int, dest: ByteBuffer, destOff: Int, destLen: Int): Int {
    if (src.hasArray() && dest.hasArray()) {
      return decompress(src.array(), srcOff + src.arrayOffset(), dest.array(), destOff + dest.arrayOffset(), destLen)
    }
    else {
      throw AssertionError("Do not support decompression on direct buffers")
    }
  }
}

/**
 * This file is forked from https://github.com/lz4/lz4-java. In particular, it forks the following file
 * net.jpountz.lz4.LZ4JavaSafeCompressor.
 *
 * It modifies the original implementation to use custom LZ4SafeUtils and SafeUtils implementations which
 * include performance improvements. Additionally, instead of allocating a new hashtable for each compress
 * call, it reuses thread-local hashtables. Comments are included to mark the changes.
 */
@Internal
object LZ4Compressor : LZ4Compressor() {
  @Suppress("DuplicatedCode")
  override fun compress(src: ByteArray, srcOff: Int, srcLen: Int, dest: ByteArray, destOff: Int, maxDestLen: Int): Int {
    SafeUtils.checkRange(src, srcOff, srcLen)
    SafeUtils.checkRange(dest, destOff, maxDestLen)
    val destEnd = destOff + maxDestLen
    if (srcLen < 65547) {
      return compress64k(src, srcOff, srcLen, dest, destOff, destEnd)
    }
    else {
      val srcEnd = srcOff + srcLen
      val srcLimit = srcEnd - 5
      val mflimit = srcEnd - 12
      var dOff = destOff
      var sOff = srcOff + 1
      var anchor = srcOff
      // Modified to use thread-local hash table
      val hashTable = biggerHashTable.get()
      Arrays.fill(hashTable, srcOff)
      label63@ while (true) {
        var forwardOff = sOff
        var step = 1
        var var18 = 1 shl SKIP_STRENGTH
        while (true) {
          sOff = forwardOff
          forwardOff += step
          step = var18++ ushr SKIP_STRENGTH
          if (forwardOff <= mflimit) {
            var excess: Int = hash(SafeUtils.readInt(src, sOff))
            var ref = SafeUtils.readInt(hashTable, excess)
            var back = sOff - ref
            SafeUtils.writeInt(hashTable, excess, sOff)
            // Modified to use explicit == false
            if (back >= 65536 || !readIntEquals(src, ref, sOff)) {
              continue
            }
            excess = commonBytesBackward(src, ref, sOff, srcOff, anchor)
            sOff -= excess
            ref -= excess
            val runLen = sOff - anchor
            var tokenOff = dOff++
            if (dOff + runLen + 8 + (runLen ushr 8) > destEnd) {
              throw LZ4Exception("maxDestLen is too small")
            }
            if (runLen >= 15) {
              SafeUtils.writeByte(dest, tokenOff, 240)
              dOff = writeLen(runLen - 15, dest, dOff)
            }
            else {
              SafeUtils.writeByte(dest, tokenOff, runLen shl 4)
            }
            wildArraycopy(src, anchor, dest, dOff, runLen)
            dOff += runLen
            while (true) {
              SafeUtils.writeShortLE(dest, dOff, back)
              dOff += 2
              sOff += 4
              val matchLen: Int = commonBytes(src, ref + 4, sOff, srcLimit)
              if (dOff + 6 + (matchLen ushr 8) > destEnd) {
                throw LZ4Exception("maxDestLen is too small")
              }
              sOff += matchLen
              if (matchLen >= 15) {
                SafeUtils.writeByte(dest, tokenOff, dest[tokenOff].toInt() or 15)
                dOff = writeLen(matchLen - 15, dest, dOff)
              }
              else {
                SafeUtils.writeByte(dest, tokenOff, dest[tokenOff].toInt() or matchLen)
              }
              if (sOff > mflimit) {
                anchor = sOff
                break
              }
              SafeUtils.writeInt(hashTable, hash(SafeUtils.readInt(src, sOff - 2)), sOff - 2)
              val h = hash(SafeUtils.readInt(src, sOff))
              ref = SafeUtils.readInt(hashTable, h)
              SafeUtils.writeInt(hashTable, h, sOff)
              back = sOff - ref
              if (back >= 65536 || !readIntEquals(src, ref, sOff)) {
                anchor = sOff++
                continue@label63
              }
              tokenOff = dOff++
              SafeUtils.writeByte(dest, tokenOff, 0)
            }
          }
          dOff = lastLiterals(src, anchor, srcEnd - anchor, dest, dOff, destEnd)
          return dOff - destOff
        }
      }
    }
  }

  override fun compress(src: ByteBuffer, srcOff: Int, srcLen: Int, dest: ByteBuffer, destOff: Int, maxDestLen: Int): Int {
    return if (src.hasArray() && dest.hasArray()) {
      this.compress(src.array(), srcOff + src.arrayOffset(), srcLen, dest.array(), destOff + dest.arrayOffset(), maxDestLen)
    }
    else {
      throw java.lang.AssertionError("Do not support compression on direct buffers")
    }
  }
}

// modified to add thread-local hash tables
private val sixtyFourKBHashTable = ThreadLocal.withInitial { ShortArray(8192) }
private val biggerHashTable = ThreadLocal.withInitial { IntArray(4096) }

@Suppress("DuplicatedCode")
private fun compress64k(src: ByteArray, srcOff: Int, srcLen: Int, dest: ByteArray, destOff: Int, destEnd: Int): Int {
  val srcEnd = srcOff + srcLen
  val srcLimit = srcEnd - 5
  val mflimit = srcEnd - 12
  var dOff = destOff
  var anchor = srcOff
  if (srcLen >= 13) {
    // Modified to use thread-local hash table
    val hashTable = sixtyFourKBHashTable.get()
    Arrays.fill(hashTable, 0.toShort())
    var sOff = srcOff + 1
    label53@ while (true) {
      var forwardOff = sOff
      var step = 1
      var var16 = 1 shl SKIP_STRENGTH
      var ref: Int
      var excess: Int
      do {
        sOff = forwardOff
        forwardOff += step
        step = var16++ ushr SKIP_STRENGTH
        if (forwardOff > mflimit) {
          break@label53
        }
        excess = hash64k(SafeUtils.readInt(src, sOff))
        ref = srcOff + SafeUtils.readShort(hashTable, excess)
        SafeUtils.writeShort(hashTable, excess, sOff - srcOff)
        // Modified to use explicit == false
      }
      while (!readIntEquals(src, ref, sOff))
      excess = commonBytesBackward(src, ref, sOff, srcOff, anchor)
      sOff -= excess
      ref -= excess
      val runLen = sOff - anchor
      var tokenOff = dOff++
      if (dOff + runLen + 8 + (runLen ushr 8) > destEnd) {
        throw LZ4Exception("maxDestLen is too small")
      }
      if (runLen >= 15) {
        SafeUtils.writeByte(dest, tokenOff, 240)
        dOff = writeLen(runLen - 15, dest, dOff)
      }
      else {
        SafeUtils.writeByte(dest, tokenOff, runLen shl 4)
      }
      wildArraycopy(src, anchor, dest, dOff, runLen)
      dOff += runLen
      while (true) {
        SafeUtils.writeShortLE(dest, dOff, (sOff - ref).toShort().toInt())
        dOff += 2
        sOff += 4
        ref += 4
        val matchLen: Int = commonBytes(src, ref, sOff, srcLimit)
        if (dOff + 6 + (matchLen ushr 8) > destEnd) {
          throw LZ4Exception("maxDestLen is too small")
        }
        sOff += matchLen
        if (matchLen >= 15) {
          SafeUtils.writeByte(dest, tokenOff, SafeUtils.readByte(dest, tokenOff).toInt() or 15)
          dOff = writeLen(matchLen - 15, dest, dOff)
        }
        else {
          SafeUtils.writeByte(dest, tokenOff, SafeUtils.readByte(dest, tokenOff).toInt() or matchLen)
        }
        if (sOff > mflimit) {
          anchor = sOff
          break@label53
        }
        SafeUtils.writeShort(hashTable, hash64k(SafeUtils.readInt(src, sOff - 2)), sOff - 2 - srcOff)
        val h: Int = hash64k(SafeUtils.readInt(src, sOff))
        ref = srcOff + SafeUtils.readShort(hashTable, h)
        SafeUtils.writeShort(hashTable, h, sOff - srcOff)
        // Modified to use explicit == false
        if (!readIntEquals(src, sOff, ref)) {
          anchor = sOff++
          break
        }
        tokenOff = dOff++
        SafeUtils.writeByte(dest, tokenOff, 0)
      }
    }
  }
  dOff = lastLiterals(src, anchor, srcEnd - anchor, dest, dOff, destEnd)
  return dOff - destOff
}

/**
 * This file is forked from https://github.com/lz4/lz4-java. In particular, it forks the following file
 * net.jpountz.lz4.LZ4SafeUtils.
 *
 * It modifies the original implementation to use Java9 array mismatch method and varhandle performance
 * improvements. Comments are included to mark the changes.
 */
// Added VarHandle
private val intPlatformNative = MethodHandles.byteArrayViewVarHandle(IntArray::class.java, ByteOrder.nativeOrder())
private val longPlatformNative = MethodHandles.byteArrayViewVarHandle(LongArray::class.java, ByteOrder.nativeOrder())

private fun readIntEquals(buf: ByteArray?, i: Int, j: Int): Boolean = SafeUtils.readInt(buf, i) == SafeUtils.readInt(buf, j)

private fun safeIncrementalCopy(dest: ByteArray, matchOff: Int, dOff: Int, matchLen: Int) {
  for (i in 0 until matchLen) {
    dest[dOff + i] = dest[matchOff + i]
  }
}

// Modified wildIncrementalCopy to mirror a version in LZ4UnsafeUtils
@Suppress("NAME_SHADOWING")
private fun wildIncrementalCopy(dest: ByteArray, matchOff: Int, dOff: Int, matchCopyEnd: Int) {
  var matchOff = matchOff
  var dOff = dOff
  if (dOff - matchOff < 4) {
    for (i in 0..3) {
      dest[dOff + i] = dest[matchOff + i]
    }
    dOff += 4
    matchOff += 4
    var dec = 0
    assert(dOff >= matchOff && dOff - matchOff < 8)
    when (dOff - matchOff) {
      1 -> matchOff -= 3
      2 -> matchOff -= 2
      3 -> {
        matchOff -= 3
        dec = -1
      }
      5 -> dec = 1
      6 -> dec = 2
      7 -> dec = 3
    }
    copy4Bytes(dest, matchOff, dest, dOff)
    dOff += 4
    matchOff -= dec
  }
  else if (dOff - matchOff < COPY_LENGTH) {
    copy8Bytes(dest, matchOff, dest, dOff)
    dOff += dOff - matchOff
  }
  while (dOff < matchCopyEnd) {
    copy8Bytes(dest, matchOff, dest, dOff)
    dOff += 8
    matchOff += 8
  }
}

// Modified to use VarHandle
private fun copy8Bytes(src: ByteArray?, sOff: Int, dest: ByteArray?, dOff: Int) {
  longPlatformNative.set(dest, dOff, longPlatformNative[src, sOff] as Long)
}

// Added to copy single int
private fun copy4Bytes(src: ByteArray?, sOff: Int, dest: ByteArray?, dOff: Int) {
  intPlatformNative.set(dest, dOff, intPlatformNative[src, sOff] as Int)
}

// Modified to use Arrays.mismatch
private fun commonBytes(b: ByteArray?, o1: Int, o2: Int, limit: Int): Int {
  val mismatch = Arrays.mismatch(b, o1, limit, b, o2, limit)
  return if (mismatch == -1) limit else mismatch
}

@Suppress("NAME_SHADOWING")
private fun commonBytesBackward(b: ByteArray, o1: Int, o2: Int, l1: Int, l2: Int): Int {
  var o1 = o1
  var o2 = o2
  var count = 0
  while (o1 > l1 && o2 > l2 && b[--o1] == b[--o2]) {
    ++count
  }
  return count
}

private fun wildArraycopy(src: ByteArray, sOff: Int, dest: ByteArray, dOff: Int, len: Int) {
  try {
    var i = 0
    while (i < len) {
      copy8Bytes(src, sOff + i, dest, dOff + i)
      i += 8
    }
    // Modified to catch IndexOutOfBoundsException instead of ArrayIndexOutOfBoundsException.
    // VarHandles throw IndexOutOfBoundsException
  }
  catch (e: IndexOutOfBoundsException) {
    throw LZ4Exception("Malformed input at offset $sOff")
  }
}

@Suppress("NAME_SHADOWING")
private fun lastLiterals(src: ByteArray,
                 sOff: Int,
                 srcLen: Int,
                 dest: ByteArray,
                 dOff: Int,
                 destEnd: Int): Int {
  var dOff = dOff
  if (dOff + srcLen + 1 + (srcLen + 255 - RUN_MASK) / 255 > destEnd) {
    throw LZ4Exception()
  }
  if (srcLen >= RUN_MASK) {
    dest[dOff++] = (RUN_MASK shl ML_BITS).toByte()
    dOff = writeLen(srcLen - RUN_MASK, dest, dOff)
  }
  else {
    dest[dOff++] = (srcLen shl ML_BITS).toByte()
  }
  // copy literals
  System.arraycopy(src, sOff, dest, dOff, srcLen)
  dOff += srcLen
  return dOff
}

@Suppress("NAME_SHADOWING")
private fun writeLen(len: Int, dest: ByteArray, dOff: Int): Int {
  var len = len
  var dOff = dOff
  while (len >= 0xFF) {
    dest[dOff++] = 0xFF.toByte()
    len -= 0xFF
  }
  dest[dOff++] = len.toByte()
  return dOff
}

private const val MEMORY_USAGE = 14
private const val NOT_COMPRESSIBLE_DETECTION_LEVEL = 6
private const val MIN_MATCH = 4
private const val HASH_LOG = MEMORY_USAGE - 2
private val SKIP_STRENGTH = NOT_COMPRESSIBLE_DETECTION_LEVEL.coerceAtLeast(2)
private const val COPY_LENGTH = 8
private const val ML_BITS = 4
private const val RUN_BITS = 8 - ML_BITS
private const val RUN_MASK = (1 shl RUN_BITS) - 1
private const val HASH_LOG_64K = HASH_LOG + 1

private fun hash(i: Int): Int {
  return i * -1640531535 ushr MIN_MATCH * 8 - HASH_LOG
}

private fun hash64k(i: Int): Int {
  return i * -1640531535 ushr MIN_MATCH * 8 - HASH_LOG_64K
}