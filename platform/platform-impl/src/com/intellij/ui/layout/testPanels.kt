// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
package com.intellij.ui.layout

import org.jetbrains.annotations.ApiStatus
import javax.swing.JPanel

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
fun separatorAndComment() : JPanel {
  return panel {
    row("Label", separated = true) {
      textField({ "abc" }, {}).comment("comment")
    }
  }
}
