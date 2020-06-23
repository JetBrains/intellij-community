// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.history.VcsRevisionNumber

object DiffVcsDataKeys {
  @JvmField
  val REVISION_INFO: Key<Pair<FilePath, VcsRevisionNumber>> = Key.create("Merge.RevisionInfo")
}