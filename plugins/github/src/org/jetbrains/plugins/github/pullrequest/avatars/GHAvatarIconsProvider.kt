// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.avatars

import org.jetbrains.annotations.CalledInAwt
import javax.swing.Icon

interface GHAvatarIconsProvider {
  @CalledInAwt
  fun getIcon(avatarUrl: String?): Icon
}