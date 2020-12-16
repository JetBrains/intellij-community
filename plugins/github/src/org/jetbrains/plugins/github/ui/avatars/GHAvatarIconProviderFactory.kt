// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.avatars

import com.intellij.util.ui.JBValue
import java.awt.Component

interface GHAvatarIconProviderFactory {
  fun create(iconSize: JBValue, component: Component): GHAvatarIconsProvider
}
