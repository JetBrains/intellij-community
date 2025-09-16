// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import org.jetbrains.icons.api.IconIdentifier
import org.jetbrains.icons.api.IconManager

interface IntelliJIconManager: IconManager {
  override fun loadIcon(id: IconIdentifier, path: String, aClass: Class<*>): SwingIcon?
  fun registerIcon(id: IconIdentifier, icon: SwingIcon)
}