// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * Cell with an icon
 */
@ApiStatus.Experimental
interface LcrIcon: LcrCellBase {

  var icon: Icon?
}