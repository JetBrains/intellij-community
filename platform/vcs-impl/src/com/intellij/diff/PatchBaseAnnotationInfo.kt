// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff

import com.intellij.openapi.vcs.FilePath
import org.jetbrains.annotations.ApiStatus

/**
 * Describes how to annotate (Git Blame) a synthetic diff/merge content that is not a committed revision by itself,
 * but is derived from one - e.g. the "Your uncommitted changes" side of a patch-conflict merge,
 * which is [baseContent] with the patch applied on top.
 *
 * Annotations are computed for the base revision referenced by [baseVersionId], and the displayed content lines
 * are mapped back to that revision using [baseContent]; lines added on top of the base render as "not committed".
 *
 * @property path the file path the base revision belongs to
 * @property baseVersionId the patch base version id (e.g. `"(revision <hash>)"`); resolved lazily to a committed revision
 * @property baseContent the base revision text, used to map displayed lines back to base-revision lines
 * @see DiffVcsDataKeys.PATCH_BASE_INFO
 */
@ApiStatus.Internal
data class PatchBaseAnnotationInfo(
  val path: FilePath,
  val baseVersionId: String,
  val baseContent: CharSequence,
)
