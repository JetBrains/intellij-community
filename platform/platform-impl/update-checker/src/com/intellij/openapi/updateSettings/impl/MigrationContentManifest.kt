// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import kotlin.math.floor

/** The system property that lets the manifest and its images use `file:` URLs. It is for local debugging only. */
private const val ALLOW_FILE_CONTENT_URLS_PROPERTY = "ide.update.migration.allow.file.urls"

private const val SUPPORTED_SCHEMA_MAJOR = 1
private const val ALLOWED_HOST = "jetbrains.com"

@ApiStatus.Internal
class MigrationImage(val light: String, val dark: String)

@ApiStatus.Internal
class MigrationImages(val sourceLogo: MigrationImage, val targetLogo: MigrationImage, val background: MigrationImage?)

@ApiStatus.Internal
class MigrationLink(val text: @NlsSafe String, val url: String)

/**
 * The texts of the migration offer in one locale. Each text is plain text, not HTML.
 */
@ApiStatus.Internal
class MigrationOfferTexts(
  val title: @NlsSafe String,
  val body: @NlsSafe String,
  val bullets: List<@NlsSafe String>,
  val learnMore: MigrationLink?,
  val primaryButton: @NlsSafe String,
  val secondaryButton: @NlsSafe String,
)

@ApiStatus.Internal
sealed interface MigrationManifestParseResult {
  class Valid(val offer: MigrationOfferTexts, val images: MigrationImages) : MigrationManifestParseResult

  class Invalid(val reason: String) : MigrationManifestParseResult
}

/**
 * Parses the content manifest of the migration and selects the offer texts for [locale].
 * The locale match tries the full tag, then the language, then `defaultLocale`.
 */
@ApiStatus.Internal
fun parseMigrationManifest(
  text: String,
  locale: Locale,
  allowFileUrls: Boolean = isFileContentUrlAllowed(),
): MigrationManifestParseResult {
  val manifest = try {
    json.decodeFromString<ManifestDto>(text)
  }
  catch (e: IllegalArgumentException) {
    return MigrationManifestParseResult.Invalid("malformed JSON: ${e.message}")
  }

  return try {
    val schemaVersion = manifest.schemaVersion ?: invalid("no 'schemaVersion'")
    if (floor(schemaVersion).toInt() != SUPPORTED_SCHEMA_MAJOR) invalid("unsupported 'schemaVersion': ${schemaVersion}")
    val defaultLocale = manifest.defaultLocale?.takeIf { it.isNotBlank() } ?: invalid("no 'defaultLocale'")
    val images = manifest.images?.toModel(allowFileUrls) ?: invalid("no 'images'")
    val offer = resolveOffer(manifest.locales, locale, defaultLocale, allowFileUrls) ?: invalid("no valid 'offer' for ${locale.toLanguageTag()}")
    MigrationManifestParseResult.Valid(offer, images)
  }
  catch (e: InvalidManifestException) {
    MigrationManifestParseResult.Invalid(e.message!!)
  }
}

/**
 * Accepts `https` URLs on `jetbrains.com` and its subdomains. Accepts `file:` URLs only when [allowFileUrls] is true.
 */
@ApiStatus.Internal
fun isAllowedContentUrl(url: String, allowFileUrls: Boolean = isFileContentUrlAllowed()): Boolean {
  val uri = try {
    URI(url)
  }
  catch (_: URISyntaxException) {
    return false
  }
  return when (uri.scheme?.lowercase(Locale.ROOT)) {
    "https" -> {
      val host = uri.host?.lowercase(Locale.ROOT) ?: return false
      host == ALLOWED_HOST || host.endsWith(".${ALLOWED_HOST}")
    }
    "file" -> allowFileUrls
    else -> false
  }
}

private fun isFileContentUrlAllowed(): Boolean = System.getProperty(ALLOW_FILE_CONTENT_URLS_PROPERTY).toBoolean()

private fun resolveOffer(
  locales: Map<String, LocaleDto>,
  locale: Locale,
  defaultLocale: String,
  allowFileUrls: Boolean,
): MigrationOfferTexts? {
  val candidates = listOf(locale.toLanguageTag(), locale.language, defaultLocale).filter { it.isNotBlank() }.distinct()
  for (candidate in candidates) {
    val entry = locales.entries.find { it.key.replace('_', '-').equals(candidate, ignoreCase = true) } ?: continue
    val offer = entry.value.offer?.toModel(allowFileUrls)
    if (offer != null) return offer
  }
  return null
}

private fun ImagesDto.toModel(allowFileUrls: Boolean): MigrationImages {
  return MigrationImages(
    sourceLogo = sourceLogo?.toModel("sourceLogo", allowFileUrls) ?: invalid("no 'images.sourceLogo'"),
    targetLogo = targetLogo?.toModel("targetLogo", allowFileUrls) ?: invalid("no 'images.targetLogo'"),
    background = background?.toModel("background", allowFileUrls),
  )
}

private fun ImageDto.toModel(name: String, allowFileUrls: Boolean): MigrationImage {
  return MigrationImage(
    light = checkedUrl(light ?: invalid("no 'images.${name}.light'"), allowFileUrls),
    dark = checkedUrl(dark ?: invalid("no 'images.${name}.dark'"), allowFileUrls),
  )
}

/**
 * Returns `null` when a text is missing, so that the next locale applies. A URL that is not allowed makes the whole manifest invalid.
 */
private fun OfferDto.toModel(allowFileUrls: Boolean): MigrationOfferTexts? {
  val learnMore = learnMore?.let { link ->
    val text = link.text?.takeIf { it.isNotBlank() } ?: return null
    MigrationLink(text, checkedUrl(link.url ?: return null, allowFileUrls))
  }
  return MigrationOfferTexts(
    title = title?.takeIf { it.isNotBlank() } ?: return null,
    body = body?.takeIf { it.isNotBlank() } ?: return null,
    bullets = bullets.takeIf { list -> list.none { it.isBlank() } } ?: return null,
    learnMore = learnMore,
    primaryButton = primaryButton?.takeIf { it.isNotBlank() } ?: return null,
    secondaryButton = secondaryButton?.takeIf { it.isNotBlank() } ?: return null,
  )
}

private fun checkedUrl(url: String, allowFileUrls: Boolean): String {
  if (!isAllowedContentUrl(url, allowFileUrls)) invalid("URL is not allowed: ${url}")
  return url
}

private class InvalidManifestException(reason: String) : Exception(reason)

private fun invalid(reason: String): Nothing = throw InvalidManifestException(reason)

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private class ManifestDto(
  val schemaVersion: Double? = null,
  val defaultLocale: String? = null,
  val images: ImagesDto? = null,
  val locales: Map<String, LocaleDto> = emptyMap(),
)

@Serializable
private class ImagesDto(val sourceLogo: ImageDto? = null, val targetLogo: ImageDto? = null, val background: ImageDto? = null)

@Serializable
private class ImageDto(val light: String? = null, val dark: String? = null)

@Serializable
private class LocaleDto(val offer: OfferDto? = null)

@Serializable
private class OfferDto(
  val title: String? = null,
  val body: String? = null,
  val bullets: List<String> = emptyList(),
  val learnMore: LinkDto? = null,
  val primaryButton: String? = null,
  val secondaryButton: String? = null,
)

@Serializable
private class LinkDto(val text: String? = null, val url: String? = null)
