// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.xdebugger.attach.XAttachHost
import org.jetbrains.annotations.Nls
import javax.swing.Icon

interface AttachHostItem {
  val host: XAttachHost
  fun getId(): String
  @Nls
  fun getPresentation(): String
  fun getIcon(): Icon
}