// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.ServerPath
import com.intellij.collaboration.util.resolveRelative
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.withScheme
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URL

/**
 * @property uri should be a normalized server uri like "https://server.com/path"
 */
@Serializable
class GitLabServerPath : ServerPath {

  var uri: String = ""
    private set

  constructor()

  constructor(uri: String) {
    require(uri.isNotEmpty())
    require(!uri.endsWith('/'))
    val validation = URI.create(uri)
    require(validation.scheme != null)
    require(validation.scheme.startsWith("http"))
    this.uri = uri
  }

  val gqlApiUri: URI
    get() = toURI().resolveRelative("api/graphql/")

  val restApiUri: URI
    get() = toURI().resolveRelative("api/v4/")

  val isDefault: Boolean
    get() = toURI().host.startsWith("gitlab.com", true) || uri.contains("/gitlab.com", true)

  fun toURL(): URL = URL("$uri/")

  override fun toURI(): URI = URI.create("$uri/")

  @NlsSafe
  override fun toString() = uri

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabServerPath) return false

    if (uri != other.uri) return false

    return true
  }

  override fun hashCode(): Int {
    return uri.hashCode()
  }

  companion object {
    val DEFAULT_SERVER = GitLabServerPath("https://gitlab.com")
  }
}

/**
 * Gets the URI for this server path with HTTP schemas replaced with HTTPS.
 */
internal fun GitLabServerPath.toHttpsNormalizedURI(): URI {
  val uri = toURI()
  return uri.withScheme(if (uri.scheme == "http") "https" else uri.scheme)
}