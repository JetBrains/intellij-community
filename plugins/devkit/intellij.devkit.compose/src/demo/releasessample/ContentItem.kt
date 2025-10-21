package com.intellij.devkit.compose.demo.releasessample

import com.intellij.openapi.util.NlsSafe
import kotlinx.datetime.LocalDate
import org.jetbrains.annotations.Nls

internal sealed class ContentItem {
  @get:Nls
  abstract val displayText: String
  abstract val imagePath: String?
  abstract val versionName: String
  abstract val releaseDate: LocalDate?
  abstract val key: Any

  data class AndroidStudio(
    @Nls override val displayText: String,
    override val imagePath: String?,
    override val versionName: String,
    val build: String,
    val platformBuild: String,
    val platformVersion: String,
    val channel: ReleaseChannel,
    override val releaseDate: LocalDate?,
    override val key: Any = build,
  ) : ContentItem()

  data class AndroidRelease(
    override val displayText: @NlsSafe String,
    override val imagePath: String?,
    override val versionName: String,
    val codename: String?,
    val apiLevel: Int,
    override val releaseDate: LocalDate?,
    override val key: Any = releaseDate ?: displayText,
  ) : ContentItem()
}

internal fun ContentItem.matches(text: String): Boolean {
  if (displayText.contains(text, ignoreCase = true)) return true
  if (versionName.contains(text, ignoreCase = true)) return true

  when (this) {
    is ContentItem.AndroidStudio -> {
      if (build.contains(text, ignoreCase = true)) return true
      if (channel.name.contains(text, ignoreCase = true)) return true
      if (platformBuild.contains(text, ignoreCase = true)) return true
      if (platformVersion.contains(text, ignoreCase = true)) return true
    }

    is ContentItem.AndroidRelease -> {
      if (codename?.contains(text, ignoreCase = true) == true) return true
      if (this.apiLevel.toString().contains(text, ignoreCase = true)) return true
    }
  }

  return false
}
