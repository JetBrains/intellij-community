// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.avatars

import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.Icon

interface GHAvatarIconsProvider {
  @RequiresEdt
  fun getIcon(avatarUrl: String?): Icon
}
