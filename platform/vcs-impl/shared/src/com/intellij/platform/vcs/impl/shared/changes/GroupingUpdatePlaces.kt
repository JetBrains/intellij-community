// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import org.jetbrains.annotations.ApiStatus

/**
 * Contains constants representing locations where changes grouping updates take place.
 * Used to pass groupings for specified place through RPC/Rhizome
 */
@ApiStatus.Internal
object GroupingUpdatePlaces {
  val SHELF_TREE: String = "SHELF_TREE"
}