// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import java.net.URI

fun URI.withFragment(newFragment: String?): URI = URI(
  scheme,
  userInfo,
  host,
  port,
  path,
  query,
  newFragment
)

fun URI.withScheme(newScheme: String): URI = URI(
  newScheme,
  userInfo,
  host,
  port,
  path,
  query,
  fragment
)

fun URI.withPath(newPath: String?): URI = URI(
  scheme,
  userInfo,
  host,
  port,
  newPath,
  query,
  fragment
)

fun URI.withQuery(newQuery: String?): URI = URI(
  scheme,
  userInfo,
  host,
  port,
  path,
  newQuery,
  fragment
)

fun URI.withPort(newPort: Int): URI = URI(
  scheme,
  userInfo,
  host,
  newPort,
  path,
  query,
  fragment
)

fun URI.changeQuery(originalUri: URI, newRawQuery: String): URI =
  fromRawParts(originalUri.scheme, originalUri.isOpaque, originalUri.rawSchemeSpecificPart,
               originalUri.rawUserInfo, originalUri.host, originalUri.port, originalUri.authority,
               originalUri.rawPath, newRawQuery, originalUri.rawFragment
  )

fun URI.fromRawParts(scheme: String?, isOpaque: Boolean, schemeSpecificPart: String?,
                     userInfo: String?, host: String?, port: Int, authority: String?,
                     path: String?, query: String?, fragment: String?): URI {
  val sb = StringBuilder()
  if (!scheme.isNullOrBlank()) {
    sb.append(scheme)
    sb.append(':')
  }
  if (isOpaque) {
    sb.append(schemeSpecificPart)
  }
  else {
    if (!host.isNullOrBlank()) {
      sb.append("//")
      if (!userInfo.isNullOrBlank()) {
        sb.append(userInfo)
        sb.append('@')
      }
      val needBrackets = (host.indexOf(':') >= 0
                          && !host.startsWith("[")
                          && !host.endsWith("]"))
      if (needBrackets) sb.append('[')
      sb.append(host)
      if (needBrackets) sb.append(']')
      if (port != -1) {
        sb.append(':')
        sb.append(port)
      }
    }
    else if (!authority.isNullOrBlank()) {
      sb.append("//")
      sb.append(authority)
    }
    if (path != null) sb.append(path)
    if (query != null) {
      sb.append('?')
      sb.append(query)
    }
  }
  if (fragment != null) {
    sb.append('#')
    sb.append(fragment)
  }
  return URI(sb.toString())
}

private fun parseParameters(input: String?): Map<String, String> {
  if (input.isNullOrBlank()) return emptyMap()

  // TODO Url-decode values?
  return input
    .split('&')
    .mapNotNull {
      val split = it.split('=', limit = 2)
      if (split.size != 2) return@mapNotNull null
      split[0] to split[1]
    }
    .toMap()
}

val URI.fragmentParameters: Map<String, String>
  get() = parseParameters(fragment)

val URI.queryParameters: Map<String, String>
  get() = parseParameters(query)

fun URI.newURIWithFragmentParameters(fragmentParameters: Map<String, String>): URI {
  val newFragment = fragmentParameters.toList().joinToString(separator = "&") { "${it.first}=${it.second}" }
  return URI(scheme, userInfo, host, port, path, query, newFragment)
}