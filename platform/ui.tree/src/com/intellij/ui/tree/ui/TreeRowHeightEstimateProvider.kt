// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree.ui

import org.jetbrains.annotations.ApiStatus

/**
 * A user object or a node of a tree that knows its height before it's rendered.
 *
 * A tree with a variable row height measures each row with its cell renderer. Until then, [DefaultTreeUI] gives the row
 * the default height set by the UI theme.
 * Each change of the node recomputes its height again. When the real height is different from the default height, the rows
 * below move when the tree computes the row height. A row outside the viewport can stay unmeasured for a long time.
 * Implement this interface when you know the height of a row without rendering, for example from the kind of the row.
 *
 * The tree asks the user object of a [javax.swing.tree.DefaultMutableTreeNode], or the tree node itself,
 * whichever of them implements this interface (a node-level implementation wins).
 *
 * A tree with a fixed row height does not use this interface.
 */
@ApiStatus.Experimental
interface TreeRowHeightEstimateProvider {
  /**
   *  The height of the node, scaled.
   *
   *  Must be fast to compute (not involve font metrics, rendering and similar things).
   *  May be different from the actual height, then it'll be computed when the node is rendered
   *  (which may cause vertical jumping of other nodes).
   *
   *  The return value of zero means using the default, defined by the `Tree.rowHeight` UI theme key.
   */
  val rowHeightEstimate: Int
}
