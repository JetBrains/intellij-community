// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.testFramework.UsefulTestCase
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

class GitReftableReaderTest : UsefulTestCase() {
  private lateinit var gitDir: Path

  override fun setUp() {
    super.setUp()
    gitDir = Files.createTempDirectory("git-reftable-test")
    Files.createDirectories(gitDir.resolve("reftable"))
  }

  @OptIn(ExperimentalPathApi::class)
  override fun tearDown() {
    try {
      gitDir.deleteRecursively()
    }
    catch (e: Exception) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test symbolic HEAD v1`() {
    val table = ReftableBuilder(version = 1).apply {
      addSymRef("HEAD", "refs/heads/master")
    }.build()
    writeTable("0001.ref", table)

    assertEquals("refs/heads/master", GitReftableReader.readHeadTarget(gitDir))
  }

  fun `test symbolic HEAD v2 sha1`() {
    val table = ReftableBuilder(version = 2, hashId = "sha1").apply {
      addSymRef("HEAD", "refs/heads/main")
    }.build()
    writeTable("0001.ref", table)

    assertEquals("refs/heads/main", GitReftableReader.readHeadTarget(gitDir))
  }

  fun `test symbolic HEAD v2 sha256`() {
    val table = ReftableBuilder(version = 2, hashId = "s256").apply {
      addSymRef("HEAD", "refs/heads/main")
    }.build()
    writeTable("0001.ref", table)

    assertEquals("refs/heads/main", GitReftableReader.readHeadTarget(gitDir))
  }

  fun `test detached HEAD returns null`() {
    val table = ReftableBuilder(version = 1).apply {
      addDirectRef("HEAD", ByteArray(20)) // SHA-1 hash
    }.build()
    writeTable("0001.ref", table)

    assertNull(GitReftableReader.readHeadTarget(gitDir))
  }

  fun `test HEAD deletion returns null`() {
    val table = ReftableBuilder(version = 1).apply {
      addDeletion("HEAD")
    }.build()
    writeTable("0001.ref", table)

    assertNull(GitReftableReader.readHeadTarget(gitDir))
  }

  fun `test HEAD not in table returns null`() {
    val table = ReftableBuilder(version = 1).apply {
      addSymRef("refs/heads/main", "refs/heads/other")
    }.build()
    writeTable("0001.ref", table)

    assertNull(GitReftableReader.readHeadTarget(gitDir))
  }

  fun `test records before HEAD`() {
    // CHERRY_PICK_HEAD sorts before HEAD ('C' < 'H')
    val table = ReftableBuilder(version = 1).apply {
      addDirectRef("CHERRY_PICK_HEAD", ByteArray(20))
      addSymRef("HEAD", "refs/heads/feature")
    }.build()
    writeTable("0001.ref", table)

    assertEquals("refs/heads/feature", GitReftableReader.readHeadTarget(gitDir))
  }

  fun `test multi-table HEAD in older`() {
    // Older table has HEAD
    val older = ReftableBuilder(version = 1).apply {
      addSymRef("HEAD", "refs/heads/master")
    }.build()
    // Newer table has only other refs
    val newer = ReftableBuilder(version = 1).apply {
      addSymRef("refs/heads/feature", "refs/heads/other")
    }.build()
    writeTable("0001.ref", older)
    writeTable("0002.ref", newer)

    assertEquals("refs/heads/master", GitReftableReader.readHeadTarget(gitDir))
  }

  fun `test multi-table HEAD overridden`() {
    // Older table has HEAD pointing to master
    val older = ReftableBuilder(version = 1).apply {
      addSymRef("HEAD", "refs/heads/master")
    }.build()
    // Newer table overrides HEAD to feature
    val newer = ReftableBuilder(version = 1).apply {
      addSymRef("HEAD", "refs/heads/feature")
    }.build()
    writeTable("0001.ref", older)
    writeTable("0002.ref", newer)

    assertEquals("refs/heads/feature", GitReftableReader.readHeadTarget(gitDir))
  }

  fun `test invalid magic throws IOException`() {
    val badFile = ByteArray(100) // all zeros, no REFT magic
    writeTable("0001.ref", badFile)

    assertThrows(IOException::class.java) { GitReftableReader.readHeadTarget(gitDir) }
  }

  fun `test unsupported version throws IOException`() {
    val table = ReftableBuilder(version = 1).apply {
      addSymRef("HEAD", "refs/heads/master")
    }.build()
    // Override version byte to 3
    table[4] = 3
    writeTable("0001.ref", table)

    assertThrows(IOException::class.java) { GitReftableReader.readHeadTarget(gitDir) }
  }

  fun `test truncated file throws IOException`() {
    // Write only the magic + version (5 bytes, not enough for a full header)
    writeTable("0001.ref", "REFT\u0001".toByteArray(Charsets.US_ASCII))

    assertThrows(IOException::class.java) { GitReftableReader.readHeadTarget(gitDir) }
  }

  fun `test empty tables list returns null`() {
    // Write empty tables.list
    Files.writeString(gitDir.resolve("reftable/tables.list"), "")

    assertNull(GitReftableReader.readHeadTarget(gitDir))
  }

  @OptIn(ExperimentalPathApi::class)
  fun `test missing reftable directory returns null`() {
    // Delete the reftable directory
    gitDir.resolve("reftable").deleteRecursively()

    assertNull(GitReftableReader.readHeadTarget(gitDir))
  }

  fun `test block type not r returns null`() {
    val table = ReftableBuilder(version = 1).apply {
      addSymRef("HEAD", "refs/heads/master")
    }.build()
    // Override block type byte (at offset 24 for v1) to 'g' (log block)
    table[24] = 'g'.code.toByte()
    writeTable("0001.ref", table)

    assertNull(GitReftableReader.readHeadTarget(gitDir))
  }

  fun `test varint basic values`() {
    // Single-byte varint: value 5
    val buf1 = byteArrayOf(0x05)
    val result1 = GitReftableReader.readVarInt(buf1, 0)
    assertEquals(5L, result1.first)
    assertEquals(1, result1.second)

    // Two-byte varint: value 200
    // Reftable varint: val = b0 & 0x7f; while (b0 & 0x80): val = ((val+1)<<7) | (next & 0x7f)
    // first byte = 0x80 | 0 = 0x80, second = 0x48.
    // Decode: val = 0x80 & 0x7f = 0; continuation: val = (0+1)<<7 | 0x48 = 128 + 72 = 200. ✓
    val buf2 = byteArrayOf(0x80.toByte(), 0x48)
    val result2 = GitReftableReader.readVarInt(buf2, 0)
    assertEquals(200L, result2.first)
    assertEquals(2, result2.second)
  }

  fun `test varint out of bounds throws IOException`() {
    val buf = byteArrayOf(0x05)
    assertThrows(IOException::class.java) { GitReftableReader.readVarInt(buf, 1) }
    assertThrows(IOException::class.java) { GitReftableReader.readVarInt(buf, 10) }
  }

  fun `test varint truncated continuation throws IOException`() {
    // Byte with continuation bit set, but no next byte
    val buf = byteArrayOf(0x80.toByte())
    assertThrows(IOException::class.java) { GitReftableReader.readVarInt(buf, 0) }
  }

  fun `test HEAD with peeled ref returns null`() {
    // value_type=0x2: two hashes (annotated tag target)
    val table = ReftableBuilder(version = 1).apply {
      addPeeledRef("HEAD", ByteArray(20), ByteArray(20))
    }.build()
    writeTable("0001.ref", table)

    assertNull(GitReftableReader.readHeadTarget(gitDir))
  }

  fun `test HEAD with long branch name`() {
    val longBranch = "refs/heads/" + "a".repeat(200)
    val table = ReftableBuilder(version = 1).apply {
      addSymRef("HEAD", longBranch)
    }.build()
    writeTable("0001.ref", table)

    assertEquals(longBranch, GitReftableReader.readHeadTarget(gitDir))
  }

  fun `test prefix compression across records`() {
    // Two refs that share a prefix: "refs/heads/a" and "refs/heads/b"
    // Prefix compression applies across consecutive records
    val table = ReftableBuilder(version = 1).apply {
      addSymRef("HEAD", "refs/heads/master")
      addDirectRef("refs/heads/a", ByteArray(20))
      addDirectRef("refs/heads/b", ByteArray(20))
    }.build()
    writeTable("0001.ref", table)

    assertEquals("refs/heads/master", GitReftableReader.readHeadTarget(gitDir))
  }

  fun `test missing table file throws IOException`() {
    // tables.list references a file that doesn't exist
    Files.writeString(gitDir.resolve("reftable/tables.list"), "0001.ref\n")

    // 0001.ref doesn't exist — should throw IOException
    assertThrows(IOException::class.java) { GitReftableReader.readHeadTarget(gitDir) }
  }

  fun `test truncated v2 header throws IOException`() {
    // Build a v2 file but truncate it between 24-28 bytes (missing hash_id)
    val table = ReftableBuilder(version = 2, hashId = "sha1").apply {
      addSymRef("HEAD", "refs/heads/main")
    }.build()
    // Truncate to 25 bytes — has v2 version but no complete hash_id field
    writeTable("0001.ref", table.copyOf(25))

    assertThrows(IOException::class.java) { GitReftableReader.readHeadTarget(gitDir) }
  }

  fun `test unknown v2 hash_id throws IOException`() {
    val table = ReftableBuilder(version = 2, hashId = "sha1").apply {
      addSymRef("HEAD", "refs/heads/main")
    }.build()
    // Corrupt hash_id at bytes 24..27 to "xxxx"
    table[24] = 'x'.code.toByte()
    table[25] = 'x'.code.toByte()
    table[26] = 'x'.code.toByte()
    table[27] = 'x'.code.toByte()
    writeTable("0001.ref", table)

    assertThrows(IOException::class.java) { GitReftableReader.readHeadTarget(gitDir) }
  }

  fun `test truncated block header throws IOException`() {
    // Build a valid header but truncate right after it (no block header bytes)
    val table = ReftableBuilder(version = 1).apply {
      addSymRef("HEAD", "refs/heads/master")
    }.build()
    // Truncate to exactly header size + 2 bytes (incomplete block header, need 4)
    writeTable("0001.ref", table.copyOf(24 + 2))

    assertThrows(IOException::class.java) { GitReftableReader.readHeadTarget(gitDir) }
  }

  fun `test corrupt prefix compression throws IOException`() {
    val table = ReftableBuilder(version = 1).apply {
      addSymRef("HEAD", "refs/heads/master")
    }.build()
    // Find the first record's prefix_length varint (at offset headerSize + 4 = 28 for v1)
    // First record should have prefix_length=0. Set it to a large value to corrupt prefix compression.
    table[24 + 4] = 99 // prefix_length=99 but prevName is empty (length 0)
    writeTable("0001.ref", table)

    assertThrows(IOException::class.java) { GitReftableReader.readHeadTarget(gitDir) }
  }

  fun `test error message includes file path`() {
    val badFile = ByteArray(100) // all zeros, no REFT magic
    writeTable("0001.ref", badFile)

    try {
      GitReftableReader.readHeadTarget(gitDir)
      fail("Expected IOException")
    }
    catch (e: IOException) {
      val msg = e.message ?: ""
      assertTrue("Error message should contain file name, got: $msg", msg.contains("0001.ref"))
    }
  }

  // --- helpers ---

  private fun writeTable(filename: String, data: ByteArray) {
    Files.write(gitDir.resolve("reftable/$filename"), data)
    // Update tables.list with all .ref files in order
    val reftableDir = gitDir.resolve("reftable")
    val refFiles = Files.list(reftableDir).use { stream ->
      stream.filter { it.fileName.toString().endsWith(".ref") }
        .map { it.fileName.toString() }
        .sorted()
        .toList()
    }
    Files.writeString(reftableDir.resolve("tables.list"), refFiles.joinToString("\n") + "\n")
  }

  /**
   * Builder for constructing synthetic reftable binary data.
   *
   * Produces valid reftable files with proper headers, prefix-compressed ref records,
   * and correct block headers. Sufficient for unit testing the parser.
   */
  private class ReftableBuilder(
    private val version: Int = 1,
    private val hashId: String = "sha1",
  ) {
    private val records = mutableListOf<RefRecord>()

    private sealed class RefRecord(val name: String) {
      class SymRef(name: String, val target: String) : RefRecord(name)
      class DirectRef(name: String, val hash: ByteArray) : RefRecord(name)
      class PeeledRef(name: String, val hash: ByteArray, val peeledHash: ByteArray) : RefRecord(name)
      class Deletion(name: String) : RefRecord(name)
    }

    fun addSymRef(name: String, target: String) {
      records.add(RefRecord.SymRef(name, target))
    }

    fun addDirectRef(name: String, hash: ByteArray) {
      records.add(RefRecord.DirectRef(name, hash))
    }

    fun addPeeledRef(name: String, hash: ByteArray, peeledHash: ByteArray) {
      records.add(RefRecord.PeeledRef(name, hash, peeledHash))
    }

    fun addDeletion(name: String) {
      records.add(RefRecord.Deletion(name))
    }

    fun build(): ByteArray {
      val headerSize = if (version == 2) 28 else 24
      val out = ByteArrayOutputStream()

      // Records data (prefix-compressed)
      val recordsData = buildRecords()

      // Block = block_type (1) + block_len (3) + records
      // block_len includes the header bytes (headerSize) for the first block
      val blockLen = headerSize + 4 + recordsData.size
      val blockHeader = ByteArrayOutputStream()
      blockHeader.write('r'.code) // block type
      blockHeader.write((blockLen shr 16) and 0xFF)
      blockHeader.write((blockLen shr 8) and 0xFF)
      blockHeader.write(blockLen and 0xFF)

      // File header
      out.write("REFT".toByteArray(Charsets.US_ASCII)) // magic
      out.write(version) // version
      // block_size = 0 (unaligned)
      out.write(0); out.write(0); out.write(0)
      // min_update_index = 0 (8 bytes)
      repeat(8) { out.write(0) }
      // max_update_index = 0 (8 bytes)
      repeat(8) { out.write(0) }

      if (version == 2) {
        out.write(hashId.toByteArray(Charsets.US_ASCII))
      }

      // Block header + records
      out.write(blockHeader.toByteArray())
      out.write(recordsData)

      return out.toByteArray()
    }

    private fun buildRecords(): ByteArray {
      val out = ByteArrayOutputStream()
      // Sort records by name (reftable requirement)
      val sorted = records.sortedBy { it.name }
      var prevName = ""

      for (record in sorted) {
        val name = record.name
        val prefixLen = commonPrefixLength(prevName, name)
        val suffix = name.substring(prefixLen)

        val valueType = when (record) {
          is RefRecord.Deletion -> 0
          is RefRecord.DirectRef -> 1
          is RefRecord.PeeledRef -> 2
          is RefRecord.SymRef -> 3
        }

        writeVarInt(out, prefixLen.toLong())
        writeVarInt(out, ((suffix.length.toLong()) shl 3) or valueType.toLong())
        out.write(suffix.toByteArray(Charsets.UTF_8))
        writeVarInt(out, 0) // update_index_delta = 0

        when (record) {
          is RefRecord.Deletion -> {}
          is RefRecord.DirectRef -> out.write(record.hash)
          is RefRecord.PeeledRef -> {
            out.write(record.hash)
            out.write(record.peeledHash)
          }
          is RefRecord.SymRef -> {
            val targetBytes = record.target.toByteArray(Charsets.UTF_8)
            writeVarInt(out, targetBytes.size.toLong())
            out.write(targetBytes)
          }
        }

        prevName = name
      }
      return out.toByteArray()
    }

    private fun commonPrefixLength(a: String, b: String): Int {
      val len = minOf(a.length, b.length)
      for (i in 0 until len) {
        if (a[i] != b[i]) return i
      }
      return len
    }

    private fun writeVarInt(out: ByteArrayOutputStream, value: Long) {
      if (value < 0x80) {
        out.write(value.toInt())
        return
      }

      // Encode in reverse: collect bytes from least significant to most
      val bytes = mutableListOf<Int>()
      var v = value
      bytes.add((v and 0x7F).toInt())
      v = (v shr 7) - 1
      while (v >= 0) {
        bytes.add(0x80 or (v and 0x7F).toInt())
        if (v < 0x80) break
        v = (v shr 7) - 1
      }
      for (b in bytes.asReversed()) {
        out.write(b)
      }
    }
  }
}
