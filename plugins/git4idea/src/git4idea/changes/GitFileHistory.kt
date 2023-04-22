// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.openapi.diff.impl.patch.TextFilePatch

interface GitFileHistory : Comparator<String> {

  fun contains(commitSha: String, filePath: String): Boolean

  override fun compare(commitSha1: String, commitSha2: String): Int

  fun getPatches(parent: String, child: String, includeFirstKnownPatch: Boolean, includeLastPatch: Boolean): List<TextFilePatch>
}