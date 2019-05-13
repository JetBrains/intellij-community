// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.URLUtil
import gnu.trove.TObjectHashingStrategy
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

private val LOG = Logger.getInstance(Urls::class.java)

// about ";" see WEB-100359
private val URI_PATTERN = Pattern.compile("^([^:/?#]+):(//)?([^/?#]*)([^?#;]*)(.*)")

object Urls {
  val caseInsensitiveUrlHashingStrategy: TObjectHashingStrategy<Url> by lazy { CaseInsensitiveUrlHashingStrategy() }

  @JvmStatic
  fun newUri(scheme: String?, path: String): UrlImpl = UrlImpl(scheme, null, path)

  @JvmStatic
  fun newUrl(scheme: String, authority: String, path: String, rawParameters: String?): UrlImpl {
    return UrlImpl(scheme, authority, path, rawParameters)
  }

  @JvmStatic
  fun newUrl(scheme: String, authority: String, path: String, parameters: Map<String, String?>): UrlImpl {
    var parametersString: String? = null
    if (parameters.isNotEmpty()) {
      val result = StringBuilder()
      result.append("?")
      encodeParameters(parameters, result)
      parametersString = result.toString()
    }
    return UrlImpl(scheme, authority, path, parametersString)
  }

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
        result.append('=')
        result.append(URLUtil.encodeURIComponent(value))
      }
    }
  }

  @JvmStatic
  fun newLocalFileUrl(path: String): Url = LocalFileUrl(FileUtilRt.toSystemIndependentName(path))

  @JvmStatic
  fun newLocalFileUrl(file: VirtualFile): Url = LocalFileUrl(file.path)

  @JvmStatic
  fun newFromEncoded(url: String): Url {
    val result = parseEncoded(url)
    LOG.assertTrue(result != null, url)
    return result!!
  }

  @JvmStatic
  fun parseEncoded(url: String): Url? = parse(url, false)

  @JvmStatic
  fun newHttpUrl(authority: String, path: String?): Url {
    return newUrl("http", authority, path)
  }

  @JvmStatic
  fun newHttpUrl(authority: String, path: String?, parameters: String?): Url {
    return UrlImpl("http", authority, path, parameters)
  }

  @JvmStatic
  fun newUrl(scheme: String, authority: String, path: String?): Url {
    return UrlImpl(scheme, authority, path)
  }

  /**
   * Url will not be normalized (see [VfsUtilCore.toIdeaUrl]), parsed as is
   */
  @JvmStatic
  fun newFromIdea(url: CharSequence): Url {
    val result = parseFromIdea(url)
    LOG.assertTrue(result != null, url)
    return result!!
  }

  // java.net.URI.create cannot parse "file:///Test Stuff" - but you don't need to worry about it - this method is aware
  @JvmStatic
  fun parseFromIdea(url: CharSequence): Url? {
    var i = 0
    val n = url.length
    while (i < n) {
      val c = url[i]
      if (c == ':') {
        // file:// or dart:core/foo
        return parseUrl(url)
      }
      else if (c == '/' || c == '\\') {
        return newLocalFileUrl(url.toString())
      }
      i++
    }
    return newLocalFileUrl(url.toString())
  }

  @JvmStatic
  fun parse(url: String, asLocalIfNoScheme: Boolean): Url? {
    if (url.isEmpty()) {
      return null
    }

    if (asLocalIfNoScheme && !URLUtil.containsScheme(url)) {
      // nodejs debug - files only in local filesystem
      return newLocalFileUrl(url)
    }
    else {
      return parseUrl(VfsUtilCore.toIdeaUrl(url))
    }
  }

  @JvmStatic
  fun parseAsJavaUriWithoutParameters(url: String): URI? {
    val asUrl = parseUrl(url) ?: return null

    try {
      return toUriWithoutParameters(asUrl)
    }
    catch (e: Exception) {
      LOG.info("Cannot parse url $url", e)
      return null
    }

  }

  private fun parseUrl(url: CharSequence): Url? {
    val urlToParse: CharSequence
    if (StringUtil.startsWith(url, "jar:file://")) {
      urlToParse = url.subSequence("jar:".length, url.length)
    }
    else {
      urlToParse = url
    }

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
  fun newFromVirtualFile(file: VirtualFile): Url {
    if (file.isInLocalFileSystem) {
      return newUri(file.fileSystem.protocol, file.path)
    }
    else {
      val url = parseUrl(file.url)
      return url ?: Urls.newUnparsable(file.path)
    }
  }

  @JvmStatic
  fun newUnparsable(string: String): UrlImpl = UrlImpl(null, null, string, null)

  @JvmOverloads
  @JvmStatic
  fun equalsIgnoreParameters(url: Url, urls: Collection<Url>, caseSensitive: Boolean = true): Boolean {
    for (otherUrl in urls) {
      if (equals(url, otherUrl, caseSensitive, true)) {
        return true
      }
    }
    return false
  }

  fun equalsIgnoreParameters(url: Url, file: VirtualFile): Boolean {
    if (file.isInLocalFileSystem) {
      return url.isInLocalFileSystem && if (SystemInfoRt.isFileSystemCaseSensitive)
        url.path == file.path
      else
        url.path.equals(file.path, ignoreCase = true)
    }
    else if (url.isInLocalFileSystem) {
      return false
    }

    val fileUrl = parseUrl(file.url)
    return fileUrl != null && fileUrl.equalsIgnoreParameters(url)
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
  fun toUriWithoutParameters(url: Url): URI {
    try {
      var externalPath = url.path
      val inLocalFileSystem = url.isInLocalFileSystem
      if (inLocalFileSystem && SystemInfoRt.isWindows && externalPath[0] != '/') {
        externalPath = "/$externalPath"
      }
      return URI(if (inLocalFileSystem) "file" else url.scheme, if (inLocalFileSystem) "" else url.authority, externalPath, null, null)
    }
    catch (e: URISyntaxException) {
      throw RuntimeException(e)
    }

  }
}

private class CaseInsensitiveUrlHashingStrategy : TObjectHashingStrategy<Url> {
  override fun computeHashCode(url: Url?) = url?.hashCodeCaseInsensitive() ?: 0

  override fun equals(url1: Url, url2: Url) = Urls.equals(url1, url2, false, false)
}