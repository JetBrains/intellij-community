// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree.ui

import org.jetbrains.annotations.ApiStatus

/**
 * The user object of a tree row that knows the height of the row before the tree measures the row.
 *
 * A tree with a variable row height measures each row with its cell renderer. Until then, [DefaultTreeUI] gives the row
 * the row height of the theme. Each change of the row starts this again. When the real height is different, the rows
 * below move when the tree measures the row. A row outside the viewport can stay unmeasured for a long time.
 * Implement this interface when you know the height of a row without rendering, for example from the kind of the row.
 *
 * The tree asks the user object of a [javax.swing.tree.DefaultMutableTreeNode], or the tree value itself.
 * A tree with a fixed row height does not ask.
 */
@ApiStatus.Experimental
interface TreeRowHeightEstimateProvider {
  /** The height of the row in pixels, as the cell renderer gives it. Zero or less gives the row height of the theme. */
  val rowHeightEstimate: Int
}
