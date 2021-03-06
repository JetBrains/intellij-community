// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.diff.impl.patch.TextFilePatch

interface GHPRFileHistory : Comparator<String> {

  fun contains(commitSha: String, filePath: String): Boolean

  override fun compare(commitSha1: String, commitSha2: String): Int

  fun getPatches(parent: String, child: String, includeFirstKnownPatch: Boolean, includeLastPatch: Boolean): List<TextFilePatch>
}