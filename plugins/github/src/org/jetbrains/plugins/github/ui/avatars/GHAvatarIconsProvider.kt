// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.avatars

import com.intellij.util.ui.codereview.avatar.CachingAvatarIconsProvider
import org.jetbrains.plugins.github.GithubIcons
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.CachingGHUserAvatarLoader
import java.awt.Image
import javax.swing.Icon

class GHAvatarIconsProvider(private val avatarsLoader: CachingGHUserAvatarLoader,
                            private val requestExecutor: GithubApiRequestExecutor)
  : CachingAvatarIconsProvider<String>(GithubIcons.DefaultAvatar) {

  fun getIcon(key: String?): Icon = super.getIcon(key, GHUIUtil.AVATAR_SIZE)

  override fun loadImage(key: String): Image? = avatarsLoader.requestAvatar(requestExecutor, key).get()
}