// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

enum class GitObjectFormat(
  val value: String,
  val hexSize: Int,
) {
  SHA1("sha1", 40),
  SHA256("sha256", 64);

  companion object {
    @JvmStatic
    fun parse(value: String?): GitObjectFormat {
      if (value == null) return SHA1
      return parseOrNull(value) ?: throw IllegalArgumentException("Unknown object format: $value")
    }

    @JvmStatic
    fun parseOrNull(value: String): GitObjectFormat? = entries.find { it.value == value }
  }
}
