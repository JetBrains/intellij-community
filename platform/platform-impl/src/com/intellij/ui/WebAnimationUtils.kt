// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.text.HtmlChunk
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Dimension

@ApiStatus.Experimental
@ApiStatus.Internal
object WebAnimationUtils {
  @Suppress("HardCodedStringLiteral")
  fun createLottieAnimationPage(lottieJson: String, lottieScript: String? = null, background: Color): String {
    val componentId = "lottie"
    val script = if (lottieScript != null) {
      HtmlChunk.tag("script").addRaw(lottieScript)
    }
    else createDownloadableLottieScriptTag()
    val body = HtmlChunk.body()
      .child(script)
      .child(HtmlChunk.div().attr("id", componentId).addRaw(""))
      .child(HtmlChunk.tag("script").addRaw("""
           const animationData = $lottieJson;
           const params = {
               container: document.getElementById('lottie'),
               renderer: 'svg',
               loop: true,
               autoplay: true,
               animationData: animationData
           };
           lottie.loadAnimation(params);
           """.trimIndent()))
    return createSingleContentHtmlPage(body, background, componentId)
  }

  fun createVideoHtmlPage(videoBase64: String, background: Color): String {
    val componentId = "video"
    val scriptText =
      """
        document.addEventListener("DOMContentLoaded", function() {
            let video = document.getElementById("$componentId");

            window.playVideo = function() {
                video.play();
            }

            window.pauseVideo = function() {
                video.pause();
            }
        });
    """.trimIndent()

    val script = HtmlChunk.tag("script").addRaw(scriptText)

    val videoTag = HtmlChunk.tag("video")
      .attr("id", componentId)
      .attr("autoplay")
      .attr("loop")
      .attr("muted")
      .child(HtmlChunk.tag("source")
               .attr("type", "video/webm")
               .attr("src", "data:video/webm;base64,$videoBase64"))
    val body = HtmlChunk.body()
      .child(script)
      .child(videoTag)

    return createSingleContentHtmlPage(body, background, componentId)
  }

  private fun createSingleContentHtmlPage(body: HtmlChunk, background: Color, componentId: String): String {
    val head = HtmlChunk.head().child(getSingleContentCssStyles(background, componentId))
    return HtmlChunk.html()
      .child(head)
      .child(body)
      .toString()
  }

  @Suppress("HardCodedStringLiteral")
  private fun getSingleContentCssStyles(background: Color, componentId: String): HtmlChunk {
    return HtmlChunk.tag("style").addRaw("""
          body {
              background-color: #${ColorUtil.toHex(background)};
              margin: 0;
              height: 100%;
              overflow: hidden;
          }
          #${componentId} {
              background-color: #${ColorUtil.toHex(background)};
              width: 100%;
              height: 100%;
              display: block;
              overflow: hidden;
              transform: translate3d(0,0,0);
              text-align: center;
              opacity: 1;
          }
          """.trimIndent())
  }

  @Throws(SerializationException::class)
  fun getLottieImageSize(lottieJson: String): Dimension {
    val json = Json { ignoreUnknownKeys = true }
    val size = json.decodeFromString<LottieImageSize>(lottieJson)
    return Dimension(size.width, size.height)
  }

  @Serializable
  private data class LottieImageSize(
    @SerialName("w") val width: Int,
    @SerialName("h") val height: Int
  )

  private fun createDownloadableLottieScriptTag(): HtmlChunk {
    return HtmlChunk.tag("script")
      .attr("src", "https://cdnjs.cloudflare.com/ajax/libs/bodymovin/5.5.10/lottie.min.js")
      .attr("integrity", "sha512-WuVUWb/eEtkYLd+Uxb51tmI1PELy432HLMrKr4CI+TpmKMr/PBBpsnjH35A0aLFK4YniNOxJ5a6vc4aMbZFGSQ==")
      .attr("crossorigin", "anonymous")
      .attr("referrerpolicy", "no-referrer").addRaw("")
  }
}