// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.text.HtmlBuilder
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
object LottieUtils {
  @Suppress("HardCodedStringLiteral")
  fun createLottieAnimationPage(lottieJson: String, lottieScript: String? = null, background: Color): String {
    val head = HtmlBuilder().append(
      HtmlChunk.tag("style").addRaw("""
          body {
              background-color: #${ColorUtil.toHex(background)};
              margin: 0;
              height: 100%;
              overflow: hidden;
          }
          #lottie {
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
    ).wrapWith(HtmlChunk.head())

    val script = if (lottieScript != null) {
      HtmlChunk.tag("script").addRaw(lottieScript)
    }
    else createDownloadableLottieScriptTag()

    val body = HtmlBuilder()
      .append(script)
      .append(HtmlChunk.div().attr("id", "lottie").addRaw(""))
      .append(HtmlChunk.tag("script").addRaw("""
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
      .wrapWith(HtmlChunk.body())

    return HtmlBuilder()
      .append(head)
      .append(body)
      .wrapWith(HtmlChunk.html())
      .toString()
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