// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.objects

import git4idea.commands.GitObjectType
import org.jetbrains.annotations.NonNls
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

internal sealed class GitObject {
  abstract val body: ByteArray
  abstract val oid: Oid
  abstract val type: GitObjectType
  abstract var persisted: Boolean

  open val dependencies: List<Oid> = listOf()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitObject) return false
    return oid == other.oid
  }

  override fun hashCode(): Int = oid.hashCode()

  class Commit(
    override val body: ByteArray,
    override val oid: Oid,
    val author: Author,
    val committer: Author,
    val parentsOids: List<Oid>,
    val treeOid: Oid,
    val message: ByteArray,
    val gpgSignature: ByteArray?,
  ) : GitObject() {
    override val type: GitObjectType = GitObjectType.COMMIT
    override var persisted: Boolean = false
    override val dependencies: List<Oid> = parentsOids + treeOid

    /**
     * Format: `name <email> timestamp timezone`
     * Example: `John Doe <john@example.com> 1234567890 +0200`
     */
    data class Author(
      val nameAndEmail: String,
      val timestampSeconds: Long,
      val timezone: String,
    ) {
      val gitFormat: String
        get() = "$nameAndEmail $timestampSeconds $timezone"

      val name: String
        get() = nameAndEmail.substringBeforeLast('<').trim()

      val email: String
        get() = nameAndEmail.substringAfterLast('<').substringBeforeLast('>').trim()

      val timestamp: String
        get() = "$timestampSeconds $timezone"

      companion object {
        @NonNls
        private val AUTHOR_REGEX = Regex("(.+) (\\d+) (.+)")

        fun parse(value: String): Author {
          val matchResult = AUTHOR_REGEX.matchEntire(value)
                            ?: error("Invalid author format: $value")

          val (nameAndEmail, timestamp, timezone) = matchResult.destructured
          return Author(nameAndEmail, timestamp.toLong(), timezone)
        }
      }
    }

    companion object {
      @NonNls
      private const val TREE_HEADER = "tree"

      @NonNls
      private const val PARENT_HEADER = "parent"

      @NonNls
      private const val AUTHOR_HEADER = "author"

      @NonNls
      private const val COMMITTER_HEADER = "committer"

      @NonNls
      private const val GPGSIG_HEADER = "gpgsig"

      @NonNls
      private val NEW_LINE_REGEX = Regex("\n(?! )") // ignores continuation lines in gpg-signature

      class ParsedData(
        val author: Author,
        val committer: Author,
        val parentsOids: List<Oid>,
        val treeOid: Oid,
        val message: ByteArray,
        val gpgSignature: ByteArray?,
      )

      /*
      tree 6492093528503dd98e257825304e5beeb1d51f23
      parent 2d5ec84702d59b06b01a85a0742de56ddcfdd1de
      author John Doe <john.doe@example.com> 1753105582 +0200
      committer John Doe <john.doe@example.com> 1753185710 +0200
      gpgsig -----BEGIN PGP SIGNATURE-----

       (lines of the signature, each with a leading space...)
       -----END PGP SIGNATURE-----

      Implement feature
       */
      fun parseBody(body: ByteArray): ParsedData {
        val bodyString = body.toString(Charsets.UTF_8)
        val (headers, message) = bodyString.split("\n\n", limit = 2).takeIf { it.size == 2 }
                                 ?: error("Invalid commit format: missing message separator")

        var treeOid: Oid? = null
        var author: Author? = null
        var committer: Author? = null
        var gpgSignature: String? = null
        val parentsOids = mutableListOf<Oid>()
        val headerLines = headers.split(NEW_LINE_REGEX)
        for (header in headerLines) {
          val keyValue = header.split(" ", limit = 2)
          if (keyValue.size < 2) continue
          val key = keyValue[0]
          val value = keyValue[1].replace("\n ", "\n")
          when (key) {
            TREE_HEADER -> {
              treeOid = Oid.fromHex(value)
            }
            PARENT_HEADER -> {
              parentsOids.add(Oid.fromHex(value))
            }
            AUTHOR_HEADER -> {
              author = Author.parse(value)
            }
            COMMITTER_HEADER -> {
              committer = Author.parse(value)
            }
            GPGSIG_HEADER -> {
              gpgSignature = value
            }
          }
        }
        requireNotNull(author) { "Commit author not found" }
        requireNotNull(committer) { "Committer not found" }
        requireNotNull(treeOid) { "Commit tree not found" }

        return ParsedData(author,
                          committer,
                          parentsOids,
                          treeOid,
                          message.toByteArray(),
                          gpgSignature?.toByteArray())
      }

      fun buildBody(
        author: Author,
        committer: Author,
        parentsOids: List<Oid>,
        treeOid: Oid,
        message: ByteArray,
        gpgSignature: ByteArray?,
      ): ByteArray {
        return ByteArrayOutputStream().use { baos ->
          baos.write("$TREE_HEADER ${treeOid.hex()}\n")
          parentsOids.forEach { parent ->
            baos.write("$PARENT_HEADER ${parent.hex()}\n")
          }
          baos.write("$AUTHOR_HEADER ${author.gitFormat}\n")
          baos.write("$COMMITTER_HEADER ${committer.gitFormat}\n")
          gpgSignature?.let { signature ->
            baos.write(GPGSIG_HEADER)
            baos.write(processGpgSignatureWithLeadingSpace(signature))
            baos.write("\n")
          }
          baos.write("\n")
          baos.write(message)
          baos.toByteArray()
        }
      }

      private fun processGpgSignatureWithLeadingSpace(gpgSignature: ByteArray): ByteArray {
        return gpgSignature.toString(Charsets.UTF_8)
          .lineSequence()
          .joinToString("\n") { " $it" }
          .toByteArray()
      }

      private fun ByteArrayOutputStream.write(str: String) {
        write(str.toByteArray())
      }
    }
  }

  class Blob(
    override val body: ByteArray,
    override val oid: Oid,
  ) : GitObject() {
    override val type: GitObjectType = GitObjectType.BLOB
    override var persisted: Boolean = false
  }

  class Tree(
    override val body: ByteArray,
    override val oid: Oid,
    val entries: Map<FileName, Entry>,
  ) : GitObject() {
    override val type: GitObjectType = GitObjectType.TREE
    override var persisted: Boolean = false
    override val dependencies: List<Oid> = entries.values.filter { it.mode != FileMode.GITLINK }.map { it.oid }

    @JvmInline
    value class FileName(val value: String) : Comparable<FileName> {
      override fun compareTo(other: FileName): Int {
        return value.compareTo(other.value)
      }
    }

    @NonNls
    enum class FileMode(val value: String) {
      GITLINK("160000"),
      SYMLINK("120000"),
      DIR("40000"),
      REGULAR("100644"),
      EXEC("100755");

      companion object {
        fun fromValue(value: String): FileMode? {
          return FileMode.entries.find { it.value == value }
        }
      }
    }

    data class Entry(
      val mode: FileMode,
      val oid: Oid,
    )

    companion object {
      fun parseBody(body: ByteArray): Map<FileName, Entry> {
        val entries = mutableMapOf<FileName, Entry>()
        var ptr = 0
        while (ptr < body.size) {
          val spacePos = body.indexOf(' '.code.toByte(), ptr)
          val mode = String(body, ptr, spacePos - ptr, StandardCharsets.UTF_8)
          ptr = spacePos + 1

          val nullPos = body.indexOf(0.toByte(), ptr)
          val name = String(body, ptr, nullPos - ptr, StandardCharsets.UTF_8)
          ptr = nullPos + 1

          val hashBytes = body.copyOfRange(ptr, ptr + Oid.HASH_LENGTH)
          ptr += Oid.HASH_LENGTH

          entries[FileName(name)] = Entry(
            FileMode.fromValue(mode) ?: error("Unknown file mode: $mode"),
            Oid.fromByteArray(hashBytes)
          )
        }
        return entries
      }

      fun buildBody(entries: Map<FileName, Entry>): ByteArray {
        return ByteArrayOutputStream().use { baos ->
          entries.toSortedMap().forEach { (name, entry) ->
            baos.writeSequence(
              entry.mode.value.toByteArray(),
              " ".toByteArray(),
              name.value.toByteArray(),
              byteArrayOf(0),
              entry.oid.toByteArray()
            )
          }
          baos.toByteArray()
        }
      }

      private fun ByteArrayOutputStream.writeSequence(vararg arrays: ByteArray) {
        arrays.forEach { write(it) }
      }

      private fun ByteArray.indexOf(byte: Byte, startIndex: Int = 0): Int {
        for (i in startIndex until size) {
          if (this[i] == byte) return i
        }
        return -1
      }
    }
  }
}