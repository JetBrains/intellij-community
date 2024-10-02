// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.Urls.parseEncoded
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.ide.BuiltInServerManager.Companion.getInstance
import java.awt.Component
import java.awt.Image
import java.awt.Toolkit
import java.awt.image.renderable.RenderableImageProducer
import java.net.MalformedURLException
import java.net.URL
import java.util.*

@ApiStatus.Internal
class JBHtmlPaneImageResolver(
  private val referenceComponent: Component,
  private val additionalImageResolver: AdditionalImageResolver?
) : Dictionary<URL, Image>() {

  @ApiStatus.Internal
  interface AdditionalImageResolver {
    /**
     * Resolves an `Image` by a URL from a `src` attribute value of an `img` tag.
     * Ideal implementation gets an image from a pre-computed map using `url` as a key.
     *
     * @return resolved image, or `null` if no image can be found by this `url`
     */
    @RequiresEdt
    fun resolveImage(url: String): Image?
  }

  override fun get(key: Any?): Image? {
    return getImage(key as? URL ?: return null)
  }

  private fun getImage(url: URL): Image? {
    val inMemory = additionalImageResolver?.resolveImage(url.toExternalForm())
    if (inMemory != null) {
      return inMemory
    }
    return Toolkit.getDefaultToolkit().createImage(
      RenderableImageProducer(
        JBHtmlPaneRenderableImage(
          builtinServerUrl(url) ?: url,
          referenceComponent
        ),
        null
      )
    )
  }

  override fun size(): Int {
    throw UnsupportedOperationException()
  }

  override fun isEmpty(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun keys(): Enumeration<URL> {
    throw UnsupportedOperationException()
  }

  override fun elements(): Enumeration<Image?> {
    throw UnsupportedOperationException()
  }

  override fun put(key: URL, value: Image?): Image? {
    throw UnsupportedOperationException()
  }

  override fun remove(key: Any): Image? {
    throw UnsupportedOperationException()
  }

  companion object {
    private fun builtinServerUrl(url: URL): URL? {
      val parsedUrl = parseEncoded(url.toExternalForm())
      if (parsedUrl == null) {
        return null
      }
      val builtInServerManager = getInstance()
      if (!builtInServerManager.isOnBuiltInWebServer(parsedUrl)) {
        return null
      }
      try {
        return URL(builtInServerManager.addAuthToken(parsedUrl).toExternalForm())
      }
      catch (e: MalformedURLException) {
        thisLogger().warn(e)
        return null
      }
    }
  }
}
