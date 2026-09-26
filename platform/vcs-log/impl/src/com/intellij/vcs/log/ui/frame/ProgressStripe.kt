// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.openapi.Disposable
import javax.swing.JComponent

@Deprecated("Use com.intellij.vcs.ui.ProgressStripe")
class ProgressStripe(targetComponent: JComponent, parent: Disposable, startDelayMs: Int) :
  com.intellij.vcs.ui.ProgressStripe(targetComponent, parent, startDelayMs)