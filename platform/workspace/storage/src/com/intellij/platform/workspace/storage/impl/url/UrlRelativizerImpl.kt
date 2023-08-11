// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.url

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.workspace.storage.url.UrlRelativizer
import org.jetbrains.annotations.TestOnly

// TODO to make architecture extensible by other plugins, we should make extension points mechanism

/**
 * The UrlRelativizerImpl class is used to convert between absolute and relative file paths.
 *
 * @param basePathNameToUrl A list of pairs containing the base path identifier and the corresponding URL.
 *                          If provided, these base paths will be added during object initialization.
 *                          Defaults to an empty list.
 */
public open class UrlRelativizerImpl(basePathNameToUrl: List<Pair<String, String>> = listOf()) : UrlRelativizer {

  internal val basePaths = mutableListOf<BasePath>()
  private val protocols = listOf("file", "jar", "jrt")

  init {
    basePathNameToUrl.forEach { addBasePathWithProtocols(it.first, it.second) }
  }

  override fun toRelativeUrl(url: String): String {
    // TODO add functionality to only apply some macros based on entity type (such as, $MODULE$ only to ModuleEntity)

    val systemIndependentUrl = FileUtil.toSystemIndependentName(url)

    for (basePath in basePaths) {
      if (FileUtil.startsWith(systemIndependentUrl, basePath.url)) {
        return "${basePath.identifier}${systemIndependentUrl.substring(basePath.url.length)}"
      }
    }

    return systemIndependentUrl
  }

  override fun toAbsoluteUrl(url: String): String {
    val systemIndependentUrl = FileUtil.toSystemIndependentName(url)

    for (basePath in basePaths) {
      if (systemIndependentUrl.startsWith(basePath.identifier)) {
        return "${basePath.url}${systemIndependentUrl.substring(basePath.identifier.length)}"
      }
    }

    return systemIndependentUrl
  }

  private fun addBasePath(identifier: String, url: String) {
    basePaths.add(BasePath(identifier, url))
  }

  /**
   * Adds a base path with protocols (such as "file", "jar", ...)
   * and various combinations of them (for example, all of "file:", "file:/",
   * "file://" are added as prefixes).
   * The base path without any protocols is also added.
   *
   * @param identifier The identifier of the base path.
   *                   Should be without "$", since these will be added.
   *                   So, pass "PROJECT_DIR" instead of "$PROJECT_DIR$"
   * @param url The URL of the base path.
   */
  public fun addBasePathWithProtocols(identifier: String, url: String) {
    val normalizedUrl = normalizeUrl(url)
    val preparedIdentifier = "$$identifier$"
    addBasePath(preparedIdentifier, normalizedUrl)
    for (protocol in protocols) {
      addBasePath("$protocol:$preparedIdentifier", "$protocol:$normalizedUrl")
      addBasePath("$protocol:/$preparedIdentifier", "$protocol:/$normalizedUrl")
      addBasePath("$protocol://$preparedIdentifier", "$protocol://$normalizedUrl")
    }

    basePaths.sort()
  }

  private fun normalizeUrl(url: String): String = StringUtil.trimTrailing(FileUtil.toSystemIndependentName(url), '/')

  @TestOnly
  public fun getAllBasePathIdentifiers(): List<String> =
    basePaths.map { basePath ->
      basePath.identifier
    }

}

internal class BasePath(internal val identifier: String, internal val url: String) : Comparable<BasePath> {

  /**
   * The length of the trimmed URL.
   * This value is used for sorting.
   *
   * This variable represents the length of the URL after removing any prefixes like "jar:", "file:", or "jrt:",
   * as well as any leading slashes.
   */
  private val trimmedUrlLength = run {
    var trimmedUrl = url.removePrefix("jar:").removePrefix("file:").removePrefix("jrt:")
    while (trimmedUrl.startsWith("/")) {
      trimmedUrl = trimmedUrl.substring(1)
    }
    trimmedUrl.length
  }

  /**
   * Compares this BasePath object with the specified BasePath object.
   * Used for sorting.
   *
   * When sorted, `trimmedUrlLength` of based paths are decreasing.
   * If they are equal, the actual order does not matter, but here they are
   * then sorted lexicographically to make testing the sorted order easier.
   *
   * Check [com.intellij.platform.workspace.storage.tests.UrlRelativizerTest.example of sorted base paths]
   * and [com.intellij.platform.workspace.storage.tests.UrlRelativizerTest.base paths should be created with protocols and be sorted by length decreasingly]
   * to see how the sorted base paths look like.
   *
   * @param other the BasePath object to be compared
   * @return a negative integer, zero, or a positive integer as this BasePath object is
   *         less than, equal to, or greater than the specified BasePath object
   */
  override fun compareTo(other: BasePath): Int {
    return when {
      this.trimmedUrlLength > other.trimmedUrlLength -> -1
      this.trimmedUrlLength < other.trimmedUrlLength -> 1
      else -> this.url.compareTo(other.url)
    }

  }

  override fun equals(other: Any?): Boolean =
    (other is BasePath) && this.identifier == other.identifier && this.url == other.url

  override fun hashCode(): Int = 31 * identifier.hashCode() + url.hashCode()

  override fun toString(): String = "BasePath(\"$identifier\" <-> \"$url\")"

}

