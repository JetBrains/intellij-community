// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.URLUtil
import gnu.trove.TObjectHashingStrategy
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

// about ";" see WEB-100359
private val URI_PATTERN = Pattern.compile("^([^:/?#]+):(//)?([^/?#]*)([^?#;]*)(.*)")

object Urls {
  val caseInsensitiveUrlHashingStrategy: TObjectHashingStrategy<Url> by lazy { CaseInsensitiveUrlHashingStrategy() }

  @JvmStatic
  fun newUri(scheme: String?, path: String): Url = UrlImpl(scheme, null, path)

  @JvmStatic
  fun newUrl(scheme: String, authority: String, path: String, rawParameters: String?): Url =
    UrlImpl(scheme, authority, path, rawParameters)

  @JvmStatic
  fun newUrl(scheme: String, authority: String, path: String, parameters: Map<String, String?>): Url =
    if (parameters.isNotEmpty()) {
      val result = StringBuilder().append('?')
      encodeParameters(parameters, result)
      UrlImpl(scheme, authority, path, result.toString())
    }
    else UrlImpl(scheme, authority, path)

  @JvmStatic
  fun encodeParameters(parameters: Map<String, String?>, result: StringBuilder) {
    val initialSize = result.length
    for ((name, value) in parameters) {
      if (result.length != initialSize) {
        result.append('&')
      }
      // https://stackoverflow.com/questions/5330104/encoding-url-query-parameters-in-java
      result.append(URLUtil.encodeURIComponent(name))
      if (value != null && value.isNotEmpty()) {
        result.append('=').append(URLUtil.encodeURIComponent(value))
      }
    }
  }

  @JvmStatic
  fun newLocalFileUrl(path: String): Url = LocalFileUrl(FileUtilRt.toSystemIndependentName(path))

  @JvmStatic
  fun newLocalFileUrl(file: VirtualFile): Url = LocalFileUrl(file.path)

  @JvmStatic
  fun newFromEncoded(url: String): Url = parseEncoded(url) ?: throw MalformedURLException("Malformed URL: ${url}")

  @JvmStatic
  fun parseEncoded(url: String): Url? = parse(url, false)

  @JvmStatic
  fun newHttpUrl(authority: String, path: String?): Url = newUrl("http", authority, path)

  @JvmStatic
  fun newHttpUrl(authority: String, path: String?, parameters: String?): Url = UrlImpl("http", authority, path, parameters)

  @JvmStatic
  fun newUrl(scheme: String, authority: String, path: String?): Url = UrlImpl(scheme, authority, path)

  /**
   * Url will not be normalized (see [VfsUtilCore.toIdeaUrl]), parsed as is
   */
  @JvmStatic
  fun newFromIdea(url: CharSequence): Url = parseFromIdea(url) ?: throw MalformedURLException("Malformed URL: ${url}")

  // java.net.URI.create cannot parse "file:///Test Stuff" - but you don't need to worry about it - this method is aware
  @JvmStatic
  fun parseFromIdea(url: CharSequence): Url? {
    for (i in 0 until url.length) {
      when (url[i]) {
        ':' -> return parseUrl(url)  // file:// or dart:core/foo
        '/', '\\' -> return newLocalFileUrl(url.toString())
      }
    }
    return newLocalFileUrl(url.toString())
  }

  @JvmStatic
  fun parse(url: String, asLocalIfNoScheme: Boolean): Url? = when {
    url.isEmpty() -> null
    asLocalIfNoScheme && !URLUtil.containsScheme(url) -> newLocalFileUrl(url)  // nodejs debug - files only in local filesystem
    else -> parseUrl(VfsUtilCore.toIdeaUrl(url))
  }

  @JvmStatic
  fun parseAsJavaUriWithoutParameters(url: String): URI? {
    val asUrl = parseUrl(url) ?: return null
    try {
      return toUriWithoutParameters(asUrl)
    }
    catch (e: Exception) {
      logger<Urls>().info("Cannot parse: ${url}", e)
      return null
    }
  }

  private fun parseUrl(url: CharSequence): Url? {
    val urlToParse = if (StringUtil.startsWith(url, "jar:file://")) url.subSequence("jar:".length, url.length) else url

    val matcher = URI_PATTERN.matcher(urlToParse)
    if (!matcher.matches()) {
      return null
    }

    var scheme = matcher.group(1)
    if (urlToParse !== url) {
      scheme = "jar:$scheme"
    }

    var authority = StringUtil.nullize(matcher.group(3))
    var path = StringUtil.nullize(matcher.group(4))
    val hasUrlSeparator = !StringUtil.isEmpty(matcher.group(2))
    if (authority == null) {
      if (hasUrlSeparator) {
        authority = ""
      }
    }
    else if (StandardFileSystems.FILE_PROTOCOL == scheme || !hasUrlSeparator) {
      path = if (path == null) authority else authority + path
      authority = if (hasUrlSeparator) "" else null
    }

    // canonicalize only if authority is not empty or file url - we should not canonicalize URL with unknown scheme (webpack:///./modules/flux-orion-plugin/fluxPlugin.ts)
    if (path != null && (!StringUtil.isEmpty(authority) || StandardFileSystems.FILE_PROTOCOL == scheme)) {
      path = FileUtil.toCanonicalUriPath(path)
    }

    return UrlImpl(scheme, authority, path, matcher.group(5))
  }

  @JvmStatic
  fun newFromVirtualFile(file: VirtualFile): Url =
    if (file.isInLocalFileSystem) newUri(file.fileSystem.protocol, file.path)
    else parseUrl(file.url) ?: newUnparsable(file.path)

  @JvmStatic
  fun newUnparsable(string: String): Url = UrlImpl(null, null, string, null)

  @JvmOverloads
  @JvmStatic
  fun equalsIgnoreParameters(url: Url, urls: Collection<Url>, caseSensitive: Boolean = true): Boolean =
    urls.any { equals(url, it, caseSensitive, true) }

  fun equalsIgnoreParameters(url: Url, file: VirtualFile): Boolean = when {
    file.isInLocalFileSystem -> url.isInLocalFileSystem && url.path.equals(file.path, ignoreCase = !SystemInfo.isFileSystemCaseSensitive)
    url.isInLocalFileSystem -> false
    else -> parseUrl(file.url)?.equalsIgnoreParameters(url) ?: false
  }

  fun equals(url1: Url?, url2: Url?, caseSensitive: Boolean, ignoreParameters: Boolean): Boolean {
    if (url1 == null || url2 == null) {
      return url1 === url2
    }

    val o1 = if (ignoreParameters) url1.trimParameters() else url1
    val o2 = if (ignoreParameters) url2.trimParameters() else url2
    return if (caseSensitive) o1 == o2 else o1.equalsIgnoreCase(o2)
  }

  @JvmStatic
  fun toUriWithoutParameters(url: Url): URI = try {
    val inLocalFileSystem = url.isInLocalFileSystem
    val scheme = if (inLocalFileSystem) "file" else url.scheme
    val authority = if (inLocalFileSystem) "" else url.authority
    val externalPath = if (inLocalFileSystem && SystemInfo.isWindows && url.path[0] != '/') "/${url.path}" else url.path
    URI(scheme, authority, externalPath, null, null)
  }
  catch (e: URISyntaxException) {
    throw RuntimeException(e)
  }
}

private class CaseInsensitiveUrlHashingStrategy : TObjectHashingStrategy<Url> {
  override fun computeHashCode(url: Url?) = url?.hashCodeCaseInsensitive() ?: 0
  override fun equals(url1: Url, url2: Url) = Urls.equals(url1, url2, caseSensitive = false, ignoreParameters = false)
}