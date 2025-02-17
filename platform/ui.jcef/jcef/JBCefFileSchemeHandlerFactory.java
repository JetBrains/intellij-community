// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.util.io.URLUtil;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefResourceHandler;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A factory for the custom "file" scheme handler.
 * <p>
 * CEF allows for loading local resources via "file" only from inside another "file" request.
 * Thus {@link JBCefBrowser#loadHTML(String, String)} creates a fake "file" request (proxied via this factory)
 * in order to allow for loading resource files from the passed html string.
 * <p>
 * All the standard "file" requests are handled by default CEF handler with default security policy.
 */
@ApiStatus.Internal
public final class JBCefFileSchemeHandlerFactory implements JBCefApp.JBCefCustomSchemeHandlerFactory {
  public static final String FILE_SCHEME_NAME = "file";
  public static final String LOADHTML_RANDOM_URL_PREFIX = FILE_SCHEME_NAME + ":///jbcefbrowser/";

  public static final Map<CefBrowser, Map<String/* url */, String /* html */>> LOADHTML_REQUEST_MAP = new WeakHashMap<>();

  @Override
  public void registerCustomScheme(@NotNull CefSchemeRegistrar registrar) {}

  @Override
  public @NotNull String getSchemeName() {
    return FILE_SCHEME_NAME;
  }

  @Override
  public @NotNull String getDomainName() {
    return "";
  }

  @Override
  public CefResourceHandler create(@NotNull CefBrowser browser, CefFrame frame, String schemeName, CefRequest request) {
    if (!FILE_SCHEME_NAME.equals(schemeName)) return null;

    String url = request.getURL();
    if (url == null) return null;
    url = normalizeUrl(url);

    // 1) check if the request has been registered
    Map<String, String> map = LOADHTML_REQUEST_MAP.get(browser);
    if (map != null) {
      String html = map.get(url);
      if (html != null) {
        return new JBCefLoadHtmlResourceHandler(html);
      }
    }
    // 2) otherwise allow default handling (with default CEF security)
    return null;
  }

  public static @NotNull String registerLoadHTMLRequest(@NotNull CefBrowser browser, @NotNull String html, @NotNull String origUrl) {
    origUrl = normalizeUrl(origUrl);
    String fileUrl = makeFileUrl(origUrl);
    getInitMap(browser).put(fileUrl, html);
    return fileUrl;
  }

  private static @NotNull Map<String, String> getInitMap(@NotNull CefBrowser browser) {
    Map<String, String> map = LOADHTML_REQUEST_MAP.get(browser);
    if (map == null) {
      synchronized (LOADHTML_REQUEST_MAP) {
        map = LOADHTML_REQUEST_MAP.get(browser);
        if (map == null) {
          LOADHTML_REQUEST_MAP.put(browser, map = Collections.synchronizedMap(new HashMap<>()));
        }
      }
    }
    return map;
  }

  public static @NotNull String makeFileUrl(@NotNull String url) {
    if (url.startsWith(FILE_SCHEME_NAME + URLUtil.SCHEME_SEPARATOR)) {
      return url;
    }
    // otherwise make a random file:// url
    return normalizeUrl(LOADHTML_RANDOM_URL_PREFIX + new Random().nextInt(Integer.MAX_VALUE) + "#url=" + url);
  }

  private static @NotNull String normalizeUrl(@NotNull String url) {
    return url.replaceAll("/$", "");
  }
}
