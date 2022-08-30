// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.avatars

import com.intellij.collaboration.ui.codereview.avatar.CachingCircleImageIconsProvider
import com.intellij.openapi.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.github.GithubIcons
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.CachingGHUserAvatarLoader
import java.awt.Image
import javax.swing.Icon

//TODO: cancel scope outside
class GHAvatarIconsProvider(private val scope: CoroutineScope,
                            private val avatarsLoader: CachingGHUserAvatarLoader,
                            private val requestExecutor: GithubApiRequestExecutor)
  : CachingCircleImageIconsProvider<String>(scope, GithubIcons.DefaultAvatar), Disposable {

  fun getIcon(key: String?): Icon = super.getIcon(key, GHUIUtil.AVATAR_SIZE)

  override suspend fun loadImage(key: String): Image? = avatarsLoader.requestAvatar(requestExecutor, key).await()

  override fun dispose() = scope.cancel()
}