/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.io;

import com.google.common.net.InetAddresses;
import com.intellij.util.net.NetUtils;
import io.netty.resolver.HostsFileEntriesResolver;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;

import static com.google.common.base.Strings.isNullOrEmpty;

public final class NettyUtil {
  // val HttpRequest.host: String?
  //  get() = headers().getAsString(HttpHeaderNames.HOST)
  public static String host(HttpRequest request) {
    return request.getHeader(HttpHeaders.Names.HOST);
  }

  // val HttpRequest.origin: String?
  //   get() = headers().getAsString(HttpHeaderNames.ORIGIN)
  public static String origin(HttpRequest request) {
    return request.getHeader(HttpHeaders.Names.ORIGIN);
  }

  //val HttpRequest.referrer: String?
  //  get() = headers().getAsString(HttpHeaderNames.REFERER)
  public static String referrer(HttpRequest request) {
    return request.getHeader(HttpHeaders.Names.REFERER);
  }

  //val HttpRequest.userAgent: String?
  //  get() = headers().getAsString(HttpHeaderNames.USER_AGENT)
  static String userAgent(HttpRequest request) {
    return request.getHeader(HttpHeaders.Names.USER_AGENT);
  }

  // fun InetAddress.isLocal() = isAnyLocalAddress || isLoopbackAddress || NetworkInterface.getByInetAddress(this) != null
  private static boolean isLocal(InetAddress address) throws SocketException {
    return address.isAnyLocalAddress() || address.isLoopbackAddress() || NetworkInterface.getByInetAddress(address) != null;
  }

  // fun isLocalHost(host: String, onlyAnyOrLoopback: Boolean, hostsOnly: Boolean = false): Boolean {
  //  if (NetUtils.isLocalhost(host)) {
  //    return true
  //  }
  //
  //  // if IP address, it is safe to use getByName (not affected by DNS rebinding)
  //  if (onlyAnyOrLoopback && !InetAddresses.isInetAddress(host)) {
  //    return false
  //  }
  //
  //  fun InetAddress.isLocal() = isAnyLocalAddress || isLoopbackAddress || NetworkInterface.getByInetAddress(this) != null
  //
  //    try {
  //    val address = InetAddress.getByName(host)
  //    if (!address.isLocal()) {
  //      return false
  //    }
  //    // be aware - on windows hosts file doesn't contain localhost
  //    // hosts can contain remote addresses, so, we check it
  //    if (hostsOnly && !InetAddresses.isInetAddress(host)) {
  //      return HostsFileEntriesResolver.DEFAULT.address(host).let { it != null && it.isLocal() }
  //    }
  //    else {
  //      return true
  //    }
  //  }
  //  catch (ignored: IOException) {
  //    return false
  //  }
  //}
  public static boolean isLocalHost(String host, boolean onlyAnyOrLoopback) {
    return isLocalHost(host, onlyAnyOrLoopback, false);
  }

  static boolean isLocalHost(String host, boolean onlyAnyOrLoopback, boolean hostsOnly) {
    if (NetUtils.isLocalhost(host)) {
      return true;
    }

    // if IP address, it is safe to use getByName (not affected by DNS rebinding)
    if (onlyAnyOrLoopback && !InetAddresses.isInetAddress(host)) {
      return false;
    }

    try {
      InetAddress address = InetAddress.getByName(host);
      if (!isLocal(address)) {
        return false;
      }
      // be aware - on windows hosts file doesn't contain localhost
      // hosts can contain remote addresses, so, we check it
      if (hostsOnly && !InetAddresses.isInetAddress(host)) {
        InetAddress addressFromHosts = HostsFileEntriesResolver.DEFAULT.address(host);
        return addressFromHosts != null && isLocal(addressFromHosts);
      }
      else {
        return true;
      }
    }
    catch (IOException ignored) {
      return false;
    }
  }

  // @JvmOverloads
  // fun HttpRequest.isLocalOrigin(onlyAnyOrLoopback: Boolean = true, hostsOnly: Boolean = false) =
  //   parseAndCheckIsLocalHost(origin, onlyAnyOrLoopback, hostsOnly) && parseAndCheckIsLocalHost(referrer, onlyAnyOrLoopback, hostsOnly)
  public static boolean isLocalOrigin(HttpRequest request) {
    return isLocalOrigin(request, true, false);
  }

  public static boolean isLocalOrigin(HttpRequest request, boolean onlyAnyOrLoopback, boolean hostsOnly) {
    return parseAndCheckIsLocalHost(origin(request), onlyAnyOrLoopback, hostsOnly) &&
           parseAndCheckIsLocalHost(referrer(request), onlyAnyOrLoopback, hostsOnly);
  }

  //private fun isTrustedChromeExtension(uri: URI): Boolean {
  //  return uri.scheme == "chrome-extension" && (uri.host == "hmhgeddbohgjknpmjagkdomcpobmllji" || uri.host == "offnedcbhjldheanlbojaefbfbllddna")
  //}
  private static boolean isTrustedChromeExtension(URI uri){
    return "chrome-extension".equals(uri.getScheme()) && ("hmhgeddbohgjknpmjagkdomcpobmllji".equals(uri.getHost()) || "offnedcbhjldheanlbojaefbfbllddna".equals(uri.getHost()));
  }

  //@JvmOverloads
  //fun parseAndCheckIsLocalHost(uri: String?, onlyAnyOrLoopback: Boolean = true, hostsOnly: Boolean = false): Boolean {
  //  if (uri == null) {
  //    return true
  //  }
  //
  //  try {
  //    val parsedUri = URI(uri)
  //    return isTrustedChromeExtension(parsedUri) || isLocalHost(parsedUri.host, onlyAnyOrLoopback, hostsOnly)
  //  }
  //  catch (ignored: Exception) {
  //  }
  //  return false
  //}

  public static boolean parseAndCheckIsLocalHost(String uri) {
    return parseAndCheckIsLocalHost(uri, true, false);
  }

  static boolean parseAndCheckIsLocalHost(String uri, boolean onlyAnyOrLoopback, boolean hostsOnly) {
    if (uri == null) {
      return true;
    }

    try {
      URI parsedUri = new URI(uri);
      return isTrustedChromeExtension(parsedUri) || isLocalHost(parsedUri.getHost(), onlyAnyOrLoopback, hostsOnly);
    }
    catch (Exception ignored) {
    }
    return false;
  }

  // forbid POST requests from browser without Origin
  //fun HttpRequest.isWriteFromBrowserWithoutOrigin(): Boolean {
  //  val userAgent = userAgent ?: return false
  //  val method = method()
  //  return origin.isNullOrEmpty() && isRegularBrowser() && (method == HttpMethod.POST || method == HttpMethod.PATCH || method == HttpMethod.PUT || method == HttpMethod.DELETE)
  //}
  static boolean isWriteFromBrowserWithoutOrigin(HttpRequest request) {
    if (request == null) return false;

    HttpMethod method = request.getMethod();
    return isNullOrEmpty(origin(request)) &&
           isRegularBrowser(request) && (method == HttpMethod.POST || method == HttpMethod.PATCH || method == HttpMethod.PUT || method == HttpMethod.DELETE);
  }

  // fun HttpRequest.isRegularBrowser() = userAgent?.startsWith("Mozilla/5.0") ?: false
  public static boolean isRegularBrowser(HttpRequest request) {
    String userAgent = userAgent(request);
    if (request == null) return false;
    return userAgent.startsWith("Mozilla/5.0");
  }
}