// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Minimal parser for the git reftable binary format.
 *
 * Reads the HEAD symbolic reference directly from `.git/reftable/` files
 * without spawning a git process. Used by [GitRecentProjectsBranchesService]
 * to display the current branch on the welcome screen for reftable-format repos.
 *
 * In reftable repos, `.git/HEAD` is a backward-compatibility stub containing
 * `ref: refs/heads/.invalid` (spec § Backward compatibility). The actual HEAD
 * is stored as a `ref_record` entry inside the reftable binary files with
 * `value_type = 0x3` (symbolic reference). This is confirmed by git's
 * `refs/reftable-backend.c` which reads HEAD via `reftable_backend_read_ref`.
 *
 * ## Format overview
 *
 * A reftable file starts with a 24-byte (v1) or 28-byte (v2) header,
 * followed by ref blocks containing sorted, prefix-compressed ref records.
 * HEAD sorts before all `refs/...` entries (`'H'` < `'r'` in ASCII), so it
 * appears near the start of the first ref block.
 *
 * Active tables are listed in `$GIT_DIR/reftable/tables.list`, one filename
 * per line, oldest to newest. Readers search in reverse order (newest first).
 *
 * ## Design decisions
 *
 * **Why not use JGit's ReftableReader?**
 * JGit (`org.eclipse.jgit.internal.storage.reftable`) provides a full reftable
 * parser, but it's not a dependency of the `git4idea` module. Adding a heavyweight
 * library dependency for reading a single ref entry is disproportionate.
 *
 * **Why not spawn `git branch --show-current`?**
 * This code runs on the welcome screen before any project is open, potentially
 * for many recent projects simultaneously. Spawning a git process per project
 * is heavier than reading ~200 bytes from disk. File I/O is also more predictable
 * (no git executable detection, no process lifecycle).
 *
 * **Why only read the first block?**
 * HEAD sorts alphabetically before all `refs/...` names. In practice, HEAD is the
 * first or one of the first entries in the first ref block. Reading more than
 * one block would add complexity (block boundary handling, alignment padding)
 * for zero practical benefit.
 *
 * **Why scan forward instead of binary-searching via index blocks?**
 * Reftable files may have index blocks for O(1) lookups, but parsing them adds
 * substantial complexity (index record format, block_position resolution).
 * Since HEAD is near the start, a linear scan of the first few records is
 * both simpler and fast enough.
 *
 * **Error handling:** Parse errors, I/O failures, and unsupported format versions
 * throw `IOException`. Legitimate "not found" cases (HEAD not present, detached HEAD,
 * no tables) return `null`. Callers should catch and log at WARN level.
 *
 * @see <a href="https://git-scm.com/docs/reftable">Reftable format specification</a>
 */
internal object GitReftableReader {
  private const val V1_HEADER_SIZE = 24
  private const val V2_HEADER_SIZE = 28
  private const val DEFAULT_READ_SIZE = 4096
  private const val HEAD_NAME = "HEAD"
  private const val SHA1_HASH_SIZE = 20
  private const val SHA256_HASH_SIZE = 32

  /**
   * Reads the HEAD symbolic ref target from reftable files under [gitDir].
   *
   * Searches tables in reverse order (newest first) per the spec:
   * "Readers must search through the stack in reverse order."
   * Returns as soon as HEAD is found in any table, or returns `null` if
   * HEAD is not present, is a direct ref (detached HEAD), or on any error.
   *
   * @param gitDir the `.git/` directory (or worktree git dir) containing `reftable/`
   * @return the symbolic ref target (e.g., `"refs/heads/master"`), or `null` if
   *         HEAD is not a symbolic ref (detached HEAD) or not found
   * @throws IOException on parse errors, I/O failures, or unsupported format
   */
  @Throws(IOException::class)
  fun readHeadTarget(gitDir: Path): String? {
    val tables = readTablesList(gitDir)
    for (tableFile in tables) {
      val result = findHeadInTable(tableFile)
      if (result != null) return result
    }
    return null
  }

  /**
   * Reads `$gitDir/reftable/tables.list` and returns table file paths, newest first.
   *
   * Per the spec: "The stack ordering file lists the current files, one per line,
   * in order from oldest (base) to newest (most recent)."
   * "Readers must search through the stack in reverse order (last reftable is examined first)."
   *
   * Only `.ref` files are returned (`.log` files contain reflogs, not ref records).
   */
  private fun readTablesList(gitDir: Path): List<Path> {
    val reftableDir = gitDir.resolve("reftable")
    val tablesListFile = reftableDir.resolve("tables.list")

    val lines = try {
      Files.readAllLines(tablesListFile)
    }
    catch (_: NoSuchFileException) {
      return emptyList()
    }

    return lines
      .asReversed()
      .filter { it.isNotBlank() }
      .map { reftableDir.resolve(it.trim()) }
  }

  /**
   * Searches for a HEAD ref_record in a single reftable file.
   *
   * Reads only the first ref block (type `'r'`) starting at the header size offset.
   * Per the spec: "The first ref block shares the same block as the file header."
   * "The first block immediately begins after the file header, at position 24."
   *
   * **Read size:** reads `max(block_size, 4096)` bytes. This is capped to avoid
   * reading large files entirely — HEAD is always near the start of the first block.
   *
   * **Version handling:**
   * - Version 1: 24-byte header, SHA-1 (20-byte hashes)
   * - Version 2: 28-byte header with explicit `hash_id` field
   *   `"sha1"` → 20-byte hashes, `"s256"` → 32-byte hashes
   * - Version 3+: unsupported, throws `IOException`
   *
   * @return the symbolic ref target string, or `null` if HEAD not found / not symbolic
   * @throws IOException on parse errors, I/O failures, or unsupported format
   */
  @VisibleForTesting
  @Throws(IOException::class)
  internal fun findHeadInTable(tableFile: Path): String? {
    val buf = readFileStart(tableFile)

    // Validate magic
    if (buf.size < 4 || buf[0] != 'R'.code.toByte() || buf[1] != 'E'.code.toByte() ||
        buf[2] != 'F'.code.toByte() || buf[3] != 'T'.code.toByte()) {
      throw IOException("Invalid reftable magic: $tableFile")
    }

    val version = buf[4].toInt() and 0xFF
    val headerSize: Int
    val hashSize: Int

    when (version) {
      1 -> {
        headerSize = V1_HEADER_SIZE
        hashSize = SHA1_HASH_SIZE
      }
      2 -> {
        if (buf.size < V2_HEADER_SIZE) throw IOException("Truncated v2 header: $tableFile")
        headerSize = V2_HEADER_SIZE
        // hash_id is at bytes 24..27
        // from https://git-scm.com/docs/reftable
        // with the 4-byte hash ID ("sha1" for SHA1 and "s256" for SHA-256) appended to the header.
        val hashId = String(buf, 24, 4, Charsets.US_ASCII)
        hashSize = when (hashId) {
          "sha1" -> SHA1_HASH_SIZE
          "s256" -> SHA256_HASH_SIZE
          else -> throw IOException("Unknown hash_id '$hashId': $tableFile")
        }
      }
      else -> throw IOException("Unsupported reftable version $version: $tableFile")
    }

    if (buf.size <= headerSize) throw IOException("Reftable file truncated after header: $tableFile")

    return findHeadInBlock(buf, headerSize, hashSize)
  }

  /**
   * Reads the beginning of a reftable file into a byte array.
   *
   * Read size is `max(block_size_from_header, DEFAULT_READ_SIZE)` bytes.
   * Short reads (small files, EOF) are handled naturally by [FileChannel.read] returning fewer bytes.
   */
  @Throws(IOException::class)
  private fun readFileStart(tableFile: Path): ByteArray {
    FileChannel.open(tableFile, StandardOpenOption.READ).use { channel ->
      // Read initial bytes to get the header and determine block size
      val initialBuf = ByteArray(DEFAULT_READ_SIZE)
      val initialByteBuf = ByteBuffer.wrap(initialBuf)
      var totalRead = 0
      while (totalRead < DEFAULT_READ_SIZE) {
        val n = channel.read(initialByteBuf)
        if (n <= 0) break
        totalRead += n
      }
      if (totalRead < 8) throw IOException("Could not read reftable header from: $tableFile")

      // Extract block_size from header: bytes 5..7 (uint24, big-endian)
      val blockSize = ((initialBuf[5].toInt() and 0xFF) shl 16) or
                      ((initialBuf[6].toInt() and 0xFF) shl 8) or
                      (initialBuf[7].toInt() and 0xFF)

      if (blockSize <= totalRead) {
        // Already have enough data (block fits in initial read, or block_size=0 meaning unaligned)
        return initialBuf.copyOf(totalRead)
      }

      // Need more data — block is larger than DEFAULT_READ_SIZE
      val fullBuf = ByteArray(blockSize)
      System.arraycopy(initialBuf, 0, fullBuf, 0, totalRead)
      val remaining = ByteBuffer.wrap(fullBuf, totalRead, blockSize - totalRead)
      while (totalRead < blockSize) {
        val n = channel.read(remaining)
        if (n <= 0) break
        totalRead += n
      }
      return if (totalRead == fullBuf.size) fullBuf else fullBuf.copyOf(totalRead)
    }
  }

  /**
   * Parses ref_records from a buffer looking for "HEAD".
   *
   * Ref records use prefix compression:
   * ```
   * varint( prefix_length )
   * varint( (suffix_length << 3) | value_type )
   * suffix
   * varint( update_index_delta )
   * value?
   * ```
   *
   * The full ref name is reconstructed as: `prev_name[0..prefix_length] + suffix`.
   *
   * Value types:
   * - `0x0`: deletion (no value data) — tombstone in transaction tables
   * - `0x1`: one object name ([hashSize] bytes) — direct ref
   * - `0x2`: two object names (2 × [hashSize] bytes) — ref + peeled target
   * - `0x3`: symbolic reference — `varint(target_len) target`
   *
   * Since refs are sorted and `"HEAD"` < `"refs/"` in ASCII,
   * we scan forward and stop as soon as the reconstructed name exceeds `"HEAD"`.
   *
   * @param buf       bytes read from the start of the reftable file
   * @param headerSize 24 for v1, 28 for v2 — offset where the first ref block starts
   * @param hashSize  20 for SHA-1, 32 for SHA-256 — needed to skip non-HEAD records
   * @return the symbolic ref target, or `null` if HEAD not found or not a symref
   */
  @Throws(IOException::class)
  private fun findHeadInBlock(buf: ByteArray, headerSize: Int, hashSize: Int): String? {
    // First ref block starts at headerSize
    // Block header: 1 byte block_type + 3 bytes block_len (uint24)
    val blockHeaderOffset = headerSize
    if (blockHeaderOffset + 4 > buf.size) throw IOException("Truncated block header")

    val blockType = buf[blockHeaderOffset].toInt().toChar()
    if (blockType != 'r') return null // no ref block

    // Ref records start after the block header (4 bytes)
    var pos = blockHeaderOffset + 4
    var prevName = ""

    while (pos < buf.size) {
      // Read prefix_length
      val prefixResult = readVarInt(buf, pos)
      val prefixLength = prefixResult.first.toInt()
      pos = prefixResult.second

      // Read (suffix_length << 3) | value_type
      val suffixTypeResult = readVarInt(buf, pos)
      val suffixType = suffixTypeResult.first
      pos = suffixTypeResult.second

      val suffixLength = (suffixType ushr 3).toInt()
      val valueType = (suffixType and 0x7).toInt()

      // Read suffix
      if (pos + suffixLength > buf.size) throw IOException("Truncated ref record suffix")
      val suffix = String(buf, pos, suffixLength, Charsets.UTF_8)
      pos += suffixLength

      // Reconstruct full name
      val fullName = if (prefixLength <= prevName.length) {
        prevName.substring(0, prefixLength) + suffix
      }
      else {
        throw IOException("Corrupt prefix compression: prefix_length $prefixLength exceeds previous name length ${prevName.length}")
      }
      prevName = fullName

      // Skip update_index_delta
      val updateIdxResult = readVarInt(buf, pos)
      pos = updateIdxResult.second

      // If we've passed HEAD alphabetically, it's not in this block
      if (fullName > HEAD_NAME) return null

      if (fullName == HEAD_NAME) {
        return when (valueType) {
          0x3 -> {
            // Symbolic ref: varint(target_len) + target
            val targetLenResult = readVarInt(buf, pos)
            val targetLen = targetLenResult.first.toInt()
            pos = targetLenResult.second
            if (pos + targetLen > buf.size) throw IOException("Truncated symbolic ref target")
            String(buf, pos, targetLen, Charsets.UTF_8)
          }
          else -> null // 0x0 (deletion), 0x1 (direct), 0x2 (direct+peeled) — not a symbolic ref
        }
      }

      // Skip value data for non-HEAD records
      when (valueType) {
        0x0 -> {} // deletion, no data
        0x1 -> pos += hashSize
        0x2 -> pos += hashSize * 2
        0x3 -> {
          val targetLenResult = readVarInt(buf, pos)
          val targetLen = targetLenResult.first.toInt()
          pos = targetLenResult.second + targetLen
        }
        else -> throw IOException("Unknown ref record value type: $valueType")
      }

      if (pos > buf.size) throw IOException("Buffer overrun while parsing ref records")
    }

    return null // HEAD not found in block
  }

  /**
   * Reads a varint from [buf] starting at [offset].
   *
   * Reftable uses the same variable-length integer encoding as git pack files:
   * ```
   * val = buf[ptr] & 0x7f
   * while (buf[ptr] & 0x80) {
   *   ptr++
   *   val = ((val + 1) << 7) | (buf[ptr] & 0x7f)
   * }
   * ```
   *
   * @return `(value, nextOffset)` pair
   * @throws IOException if [offset] is out of bounds or the varint is truncated
   */
  @VisibleForTesting
  @Throws(IOException::class)
  internal fun readVarInt(buf: ByteArray, offset: Int): Pair<Long, Int> {
    if (offset >= buf.size) throw IOException("Varint offset out of bounds")
    var ptr = offset
    var value = (buf[ptr].toInt() and 0x7F).toLong()
    while (buf[ptr].toInt() and 0x80 != 0) {
      ptr++
      if (ptr >= buf.size) throw IOException("Truncated varint")
      value = ((value + 1) shl 7) or (buf[ptr].toInt() and 0x7F).toLong()
    }
    return Pair(value, ptr + 1)
  }
}
