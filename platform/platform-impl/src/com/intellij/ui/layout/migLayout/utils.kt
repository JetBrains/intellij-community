// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout.migLayout

import java.awt.Dimension

fun Dimension.copy(width: Int? = null, height: Int? = null) =
  Dimension(width ?: this.width, height ?: this.height)