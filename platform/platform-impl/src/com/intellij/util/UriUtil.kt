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

val URI.fragmentParameters: Map<String, String>
  get() {
    // TODO Url-decode values?
    if (fragment.isNullOrBlank()) return emptyMap()

    return fragment
      .split('&')
      .mapNotNull {
        val split = it.split('=', limit = 2)
        if (split.size != 2) return@mapNotNull null
        split[0] to split[1]
      }
      .toMap()
  }

fun URI.newURIWithFragmentParameters(fragmentParameters: Map<String, String>): URI {
  val newFragment = fragmentParameters.toList().joinToString(separator = "&") { "${it.first}=${it.second}" }
  return URI(scheme, userInfo, host, port, path, query, newFragment)
}