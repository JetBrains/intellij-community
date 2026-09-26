// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile

object DiffVcsDataKeys {
  @JvmField
  val REVISION_INFO: Key<Pair<FilePath, VcsRevisionNumber>> = Key.create("Merge.RevisionInfo")

  /**
   * Marks a [com.intellij.diff.contents.DiffContent] that corresponds to the current state of the given working-tree file,
   * even when the content is not a [com.intellij.diff.contents.FileContent] (e.g. a side of a merge built from plain text).
   * Used to enable VCS annotations (Git Blame) for such a content by annotating the working file directly.
   */
  @JvmField
  val LOCAL_FILE: Key<VirtualFile> = Key.create("Merge.LocalFile")

  /**
   * Marks a [com.intellij.diff.contents.DiffContent] that is derived from a committed base revision plus extra
   * (uncommitted) changes - e.g. the "Your uncommitted changes" side of a patch-conflict merge.
   * Used to enable VCS annotations (Git Blame) for such a content by annotating the base revision and mapping
   * the displayed lines back to it; lines added on top of the base render as "not committed yet".
   */
  @JvmField
  val PATCH_BASE_INFO: Key<PatchBaseAnnotationInfo> = Key.create("Merge.PatchBaseInfo")
}