/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github.pullrequests.util

import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.EventDispatcher
import com.intellij.util.ImageLoader
import com.intellij.util.containers.SoftValueHashMap
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import icons.GithubIcons
import org.imgscalr.Scalr
import org.jetbrains.annotations.CalledInAwt
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.image.BufferedImage
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.Icon

class GithubWebImageLoader {
  companion object {
    @JvmStatic
    fun getInstance(): GithubWebImageLoader = ServiceManager.getService(GithubWebImageLoader::class.java)


    private val LOG = Logger.getInstance(GithubWebImageLoader::class.java)

    private val DEFAULT_AVATAR_KEY = "default_avatar"

    // Load up to this much images in a single chunk.
    private val REQUESTS_CHUNK_SIZE = 3
    private val MAX_SCHEDULE_DELAY = 100L
  }

  @CalledInAwt
  @JvmOverloads
  fun createIcon(url: String,
                 placeholder: Icon = EmptyIcon.create(32)): Icon {
    return MyIcon(placeholder, getPromise(url), null)
  }

  @CalledInAwt
  @JvmOverloads
  fun createIcon(url: String,
                 dimension: Dimension,
                 placeholder: Icon = EmptyIcon.create(dimension.width, dimension.height)): Icon {
    return MyIcon(placeholder, getPromise(url, dimension), dimension)
  }

  @CalledInAwt
  fun createDefaultAvatarIcon(dimension: Dimension): Icon {
    return createIcon(DEFAULT_AVATAR_KEY, dimension)
  }


  fun addListener(listener: Listener) {
    eventDispatcher.addListener(listener)
  }

  fun removeListener(listener: Listener) {
    eventDispatcher.removeListener(listener)
  }

  interface Listener : EventListener {
    @CalledInAwt
    fun iconsUpdated()
  }


  private val imageMap = SoftValueHashMap<String, LoadingPromise>()
  private val scaledImageMap = SoftValueHashMap<Pair<String, Dimension>, ScalingPromise>()

  private val requestQueue = ArrayList<LoadingPromise>()
  private var isLoadingScheduled = false

  private val eventDispatcher = EventDispatcher.create(Listener::class.java)

  init {
    ApplicationManager.getApplication().invokeLater {
      getPromise(DEFAULT_AVATAR_KEY)
    }
  }

  @CalledInAwt
  private fun getPromise(url: String): LoadingPromise {
    return imageMap.getOrPut(url) {
      val promise = LoadingPromise(url)
      requestQueue.add(promise)
      scheduleImageLoading()
      promise
    }
  }

  @CalledInAwt
  private fun getPromise(url: String, dimension: Dimension): ScalingPromise {
    return scaledImageMap.getOrPut(Pair(url, dimension)) {
      ScalingPromise(getPromise(url), dimension)
    }
  }

  @CalledInAwt
  private fun scheduleImageLoading() {
    if (requestQueue.size >= REQUESTS_CHUNK_SIZE) {
      launchImageLoading()
      return
    }

    if (isLoadingScheduled) return
    isLoadingScheduled = true

    JobScheduler.getScheduler().schedule(
        {
          ApplicationManager.getApplication().invokeLater {
            isLoadingScheduled = false
            launchImageLoading()
          }
        }, MAX_SCHEDULE_DELAY, TimeUnit.MILLISECONDS
    )
  }

  @CalledInAwt
  private fun launchImageLoading() {
    val requests = ArrayList(requestQueue)
    requestQueue.clear()

    for (index in 0..requests.size - 1 step REQUESTS_CHUNK_SIZE) {
      val endIndex = Math.min(index + REQUESTS_CHUNK_SIZE, requests.size)
      val chunk = requests.subList(index, endIndex)
      ApplicationManager.getApplication().executeOnPooledThread { doLoadRequests(chunk) }
    }
  }

  private fun doLoadRequests(requests: List<LoadingPromise>) {
    if (requests.isEmpty()) return

    for (request in requests) {
      val loadedImage = doLoadImage(request.url)
      request.complete(loadedImage)
    }

    ApplicationManager.getApplication().invokeLater {
      eventDispatcher.multicaster.iconsUpdated()
    }
  }

  private fun doLoadImage(urlStr: String): BufferedImage? {
    if (urlStr == DEFAULT_AVATAR_KEY) {
      val image = ImageLoader.loadFromResource("/org/jetbrains/plugins/github/default_avatar.png", GithubIcons::class.java)
      return if (image != null) ImageUtil.toBufferedImage(image) else null
    }

    try {
      val url = resolveRedirect(URL(urlStr))
      return ImageIO.read(url)
    }
    catch (e: Throwable) {
      LOG.debug(e)
      return null
    }
  }

  private fun resolveRedirect(url: URL): URL {
    val connection = url.openConnection()
    if (connection !is HttpURLConnection) return url

    connection.connect()
    try {
      val responseCode = connection.responseCode
      if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
        val location = connection.getHeaderField("Location")
        if (!StringUtil.isEmptyOrSpaces(location)) return URL(location)
      }
      return url
    }
    finally {
      connection.disconnect()
    }
  }

  private class MyIcon(val placeholder: Icon, val promise: ImagePromise, val dimension: Dimension?) : Icon {

    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
      val image = promise.getImage()
      if (image != null) {
        val shift = getPaintShift(image.width, image.height)
        UIUtil.drawImage(g, image, x + shift.x, y + shift.y, c)
      }
      else {
        val shift = getPaintShift(placeholder.iconWidth, placeholder.iconHeight)
        placeholder.paintIcon(c, g, x + shift.x, y + shift.y)
      }
    }

    override fun getIconWidth(): Int {
      if (dimension != null) return dimension.width

      val image = promise.getImage()
      if (image != null) return image.width

      return placeholder.iconWidth
    }

    override fun getIconHeight(): Int {
      if (dimension != null) return dimension.height

      val image = promise.getImage()
      if (image != null) return image.height

      return placeholder.iconHeight
    }

    private fun getPaintShift(width: Int, height: Int): Point {
      if (dimension == null) return Point(0, 0)
      val xShift = Math.max(0, (dimension.width - width) / 2)
      val yShift = Math.max(0, (dimension.height - height) / 2)
      return Point(xShift, yShift)
    }
  }

  private class LoadingPromise(val url: String) : ImagePromise {
    private var loadedImage: BufferedImage? = null

    fun complete(image: BufferedImage?) {
      loadedImage = image
    }

    override fun getImage(): BufferedImage? {
      return loadedImage
    }
  }

  private class ScalingPromise(private var promise: ImagePromise?, private var dimension: Dimension?) : ImagePromise {
    private var scaledImage: BufferedImage? = null

    override fun getImage(): BufferedImage? {
      if (scaledImage == null && promise != null && dimension != null) {
        val image = promise!!.getImage()
        if (image != null) {
          scaledImage = Scalr.resize(ImageUtil.toBufferedImage(image), Scalr.Method.ULTRA_QUALITY, dimension!!.width, dimension!!.height)
          // allow to gc original image
          promise = null
          dimension = null
        }
      }
      return scaledImage
    }
  }

  private interface ImagePromise {
    fun getImage(): BufferedImage?
  }
}
