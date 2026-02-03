// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.vcs.log.VcsLogUi
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a location of the [VcsLogUi].
 *
 * Location information is used to create, select, close tabs and to identify visible tabs to refresh them when needed.
 *
 * @see VcsLogManager.createLogUi
 */
@ApiStatus.Internal
enum class VcsLogTabLocation {
  TOOL_WINDOW,

  @ApiStatus.Experimental
  EDITOR,

  @ApiStatus.Experimental
  STANDALONE;
}