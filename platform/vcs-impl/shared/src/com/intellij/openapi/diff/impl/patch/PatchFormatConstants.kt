// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * The markers of the patch text that `UnifiedDiffWriter` writes and `PatchReader` reads.
 */
@ApiStatus.Internal
object PatchFormatConstants {
  const val ADDITIONAL_PREFIX: @NonNls String = "IDEA additional info:"
  const val ADD_INFO_HEADER: @NonNls String = "Subsystem: "
  const val ADD_INFO_LINE_START: @NonNls String = "<+>"
  const val NO_NEWLINE_SIGNATURE: @NonNls String = "\\ No newline at end of file"
  const val DEV_NULL: @NonNls String = "/dev/null"
  const val A_PREFIX: @NonNls String = "a/"
  const val B_PREFIX: @NonNls String = "b/"

  /**
   * The subsystem of the additional info that holds the base revision text, the name of `BaseRevisionTextPatchEP`.
   */
  const val BASE_REVISION_TEXT_SUBSYSTEM: @NonNls String = "com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP"
}
