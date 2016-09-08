/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io

import com.google.common.net.InetAddresses
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.net.NetUtils
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.ssl.SslHandler
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface

val Channel.uriScheme: String
  get() = if (pipeline().get(SslHandler::class.java) == null) "http" else "https"

val HttpRequest.host: String?
  get() = headers().getAsString(HttpHeaderNames.HOST)

val HttpRequest.origin: String?
  get() = headers().getAsString(HttpHeaderNames.ORIGIN)

val HttpRequest.referrer: String?
  get() = headers().getAsString(HttpHeaderNames.REFERER)

val HttpRequest.userAgent: String?
  get() = headers().getAsString(HttpHeaderNames.USER_AGENT)

inline fun <T> ByteBuf.releaseIfError(task: () -> T): T {
  try {
    return task()
  }
  catch (e: Exception) {
    try {
      release()
    }
    finally {
      throw e
    }
  }
}

fun isLocalHost(host: String, onlyAnyOrLoopback: Boolean, hostsOnly: Boolean = false): Boolean {
  if (NetUtils.isLocalhost(host)) {
    return true
  }

  // if IP address, it is safe to use getByName (not affected by DNS rebinding)
  if (onlyAnyOrLoopback && !InetAddresses.isInetAddress(host)) {
    return false
  }

  fun InetAddress.isLocal() = isAnyLocalAddress || isLoopbackAddress || NetworkInterface.getByInetAddress(this) != null

  try {
    val address = InetAddress.getByName(host)
    if (!address.isLocal()) {
      return false
    }
    // be aware - on windows hosts file doesn't contain localhost
    // hosts can contain remote addresses, so, we check it
    if (hostsOnly && !InetAddresses.isInetAddress(host)) {
      return io.netty.resolver.HostsFileEntriesResolver.DEFAULT.address(host).let { it != null && it.isLocal() }
    }
    else {
      return true
    }
  }
  catch (ignored: IOException) {
    return false
  }
}

@JvmOverloads
fun HttpRequest.isLocalOrigin(onlyAnyOrLoopback: Boolean = true, hostsOnly: Boolean = false) = parseAndCheckIsLocalHost(origin, onlyAnyOrLoopback, hostsOnly) && parseAndCheckIsLocalHost(referrer, onlyAnyOrLoopback, hostsOnly)

private fun isTrustedChromeExtension(url: Url): Boolean {
  return url.scheme == "chrome-extension" && (url.authority == "hmhgeddbohgjknpmjagkdomcpobmllji" || url.authority == "offnedcbhjldheanlbojaefbfbllddna")
}

private val Url.host: String?
  get() = authority?.let {
    val portIndex = it.indexOf(':')
    if (portIndex > 0) it.substring(0, portIndex) else it
  }

@JvmOverloads
fun parseAndCheckIsLocalHost(uri: String?, onlyAnyOrLoopback: Boolean = true, hostsOnly: Boolean = false): Boolean {
  if (uri == null || uri == "about:blank") {
    return true
  }

  try {
    val parsedUri = Urls.parse(uri, false) ?: return false
    val host = parsedUri.host
    return host != null && (isTrustedChromeExtension(parsedUri) || isLocalHost(host, onlyAnyOrLoopback, hostsOnly))
  }
  catch (ignored: Exception) {
  }
  return false
}

fun HttpRequest.isRegularBrowser() = userAgent?.startsWith("Mozilla/5.0") ?: false

// forbid POST requests from browser without Origin
fun HttpRequest.isWriteFromBrowserWithoutOrigin(): Boolean {
  val method = method()
  return origin.isNullOrEmpty() && isRegularBrowser() && (method == HttpMethod.POST || method == HttpMethod.PATCH || method == HttpMethod.PUT || method == HttpMethod.DELETE)
}

fun ByteBuf.readUtf8() = toString(Charsets.UTF_8)

fun ByteBuf.writeUtf8(data: CharSequence) = writeCharSequence(data, Charsets.UTF_8)