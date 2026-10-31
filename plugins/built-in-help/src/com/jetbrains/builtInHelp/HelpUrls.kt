// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal object HelpUrls {
  fun localePath(locale: String): String = if (locale.lowercase(Locale.ROOT) == "zh-cn") "/zh-cn" else ""

  fun entryUrl(port: Int, helpId: String?, keymap: String?, locale: String): String {
    val url = "http://127.0.0.1:$port${localePath(locale)}/help/?${encode(helpId ?: "top")}"
    return withKeymap(url, keymap)
  }

  fun withKeymap(url: String, keymap: String?): String {
    if (keymap == null) return url
    val fragment = if ('#' in url) "#${url.substringAfter('#')}" else ""
    val base = url.substringBefore('#')
    val parameters = if ('?' in base) base.substringAfter('?').split('&').filter {
      it.isNotEmpty() && URLDecoder.decode(it.substringBefore('='), StandardCharsets.UTF_8) != "keymap"
    } else emptyList()
    return base.substringBefore('?') + "?" + (parameters + "keymap=${encode(keymap)}").joinToString("&") + fragment
  }

  @JvmStatic
  fun isValidBaseUrl(value: String): Boolean {
    if (value.isBlank()) return true
    return try {
      val uri = URI(value.trim())
      uri.scheme in setOf("http", "https") && !uri.host.isNullOrEmpty() && uri.userInfo == null &&
      uri.query == null && uri.fragment == null
    }
    catch (_: java.net.URISyntaxException) {
      false
    }
  }

  fun onlineUrl(helpId: String, keymap: String?, baseUrl: String, defaultUrl: String?): String? {
    val base = baseUrl.trim()
    if (!isValidBaseUrl(base)) return null
    if (base.isEmpty() || base.trimEnd('/') == Utils.BASE_HELP_URL.trimEnd('/')) return defaultUrl?.let { withKeymap(it, keymap) }
    return withKeymap("${base.trimEnd('/')}/?${encode(helpId)}", keymap)
  }

  private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
