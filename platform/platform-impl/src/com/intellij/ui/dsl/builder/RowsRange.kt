// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus

/**
 * Grouped rows range to perform operations on them. All rows use parent grid
 */
@ApiStatus.NonExtendable
interface RowsRange {

  fun visible(isVisible: Boolean): RowsRange

  fun visibleIf(predicate: ComponentPredicate): RowsRange

  fun enabled(isEnabled: Boolean): RowsRange

  fun enabledIf(predicate: ComponentPredicate): RowsRange
}