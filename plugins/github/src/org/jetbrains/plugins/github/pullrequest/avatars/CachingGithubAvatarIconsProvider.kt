// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.avatars

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import icons.GithubIcons
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

/**
 * @param component which will be repainted when icons are loaded
 */
internal class CachingGithubAvatarIconsProvider(private val avatarsLoader: CachingGithubUserAvatarLoader,
                                                private val imagesResizer: GithubImageResizer,
                                                private val requestExecutor: GithubApiRequestExecutor,
                                                private val iconSize: JBValue,
                                                private val component: Component) : Disposable {

  private val scaleContext = JBUI.ScaleContext.create(component)
  private var defaultIcon = createDefaultIcon(iconSize.get())
  private val icons = mutableMapOf<GithubUser, Icon>()

  init {
    LowMemoryWatcher.register(Runnable { icons.clear() }, this)
  }

  private fun createDefaultIcon(size: Int): Icon {
    val standardDefaultAvatar = GithubIcons.DefaultAvatar_40
    val scale = size.toFloat() / standardDefaultAvatar.iconWidth.toFloat()
    return IconUtil.scale(standardDefaultAvatar, null, scale)
  }

  @CalledInAwt
  fun getIcon(user: GithubUser): Icon {
    val iconSize = iconSize.get()

    // so that icons are rescaled when any scale changes (be it font size or current DPI)
    if (scaleContext.update(JBUI.ScaleContext.create(component))) {
      defaultIcon = createDefaultIcon(iconSize)
      icons.clear()
    }

    return icons.getOrPut(user) {
      val icon = DelegatingIcon(defaultIcon)
      avatarsLoader
        .requestAvatar(requestExecutor, user)
        .thenCompose<Image?> {
          if (it != null) imagesResizer.requestImageResize(it, iconSize, scaleContext)
          else CompletableFuture.completedFuture(null)
        }
        .thenAccept {
          if (it != null) runInEdt {
            icon.delegate = IconUtil.createImageIcon(it)
            component.repaint()
          }
        }

      icon
    }
  }

  private class DelegatingIcon(var delegate: Icon) : Icon {
    override fun getIconHeight() = delegate.iconHeight
    override fun getIconWidth() = delegate.iconWidth
    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) = delegate.paintIcon(c, g, x, y)
  }

  override fun dispose() {}

  // helper to avoid passing all the services to clients
  class Factory(private val avatarsLoader: CachingGithubUserAvatarLoader,
                private val imagesResizer: GithubImageResizer,
                private val requestExecutor: GithubApiRequestExecutor) {
    fun create(iconSize: JBValue, component: Component) = CachingGithubAvatarIconsProvider(avatarsLoader, imagesResizer,
                                                                                           requestExecutor, iconSize, component)
  }
}
