// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import org.jetbrains.annotations.NonNls

@NonNls
enum class GitObjectType(val tag: String) {
  COMMIT("commit"), BLOB("blob"), TREE("tree");

  companion object {
    fun fromTag(tag: String): GitObjectType? {
      return entries.find { it.tag == tag }
    }
  }
}