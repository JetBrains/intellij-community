// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class JetBrainsProtocolHandler {
  public static final String PROTOCOL = "jetbrains://";
  private static String ourMainParameter = null;
  private static String ourCommand = null;
  public static final String REQUIRED_PLUGINS_KEY = "idea.required.plugins.id";
  private static final Map<String, String> ourParameters = new HashMap<>(0);
  private static boolean initialized = false;

  public static void processJetBrainsLauncherParameters(@NotNull String url) {
    System.setProperty(JetBrainsProtocolHandler.class.getName(), url);

    // parse without protocol, otherwise first path component will be considered as host
    URI uri = URI.create(url.substring(PROTOCOL.length()));

    String path = uri.getPath();
    List<String> urlParts = StringUtil.split(path, "/");
    // expect at least platform prefix and command name
    if (urlParts.size() < 2) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.print("Wrong URL: " + PROTOCOL + url);
      return;
    }

    ourCommand = urlParts.get(1);
    ourMainParameter = ContainerUtil.getOrElse(urlParts, 2, null);
    ourParameters.clear();
    computeParameters(uri.getRawQuery(), ourParameters);

    initialized = true;
  }

  // well, Netty cannot be added as dependency and so, QueryStringDecoder cannot be used
  private static void computeParameters(@NotNull String rawQuery, @SuppressWarnings("SameParameterValue") @NotNull Map<String, String> parameters) {
    if (StringUtilRt.isEmpty(rawQuery)) {
      return;
    }

    for (String keyValue : StringUtil.split(rawQuery, "&")) {
      if (keyValue.contains("=")) {
        int ind = keyValue.indexOf('=');
        String key = URLUtil.unescapePercentSequences(keyValue, 0, ind).toString();
        String value = URLUtil.unescapePercentSequences(keyValue, ind + 1, keyValue.length()).toString();
        if (REQUIRED_PLUGINS_KEY.equals(key)) {
          System.setProperty(key, value);
        }
        else {
          parameters.put(key, value);
        }
      }
      else {
        parameters.put(keyValue, "");
      }
    }
  }

  @Nullable
  public static String getCommand() {
    init();
    return ourCommand;
  }

  private static void init() {
    if (initialized) return;
    String property = System.getProperty(JetBrainsProtocolHandler.class.getName());
    if (property != null && property.startsWith(PROTOCOL)) {
      processJetBrainsLauncherParameters(property);
    }
  }

  public static String getMainParameter() {
    init();
    return ourMainParameter;
  }

  public static boolean appStartedWithCommand() {
    String property = System.getProperty(JetBrainsProtocolHandler.class.getName());
    return property != null && property.startsWith(PROTOCOL);
  }

  public static void clear() {
    ourCommand = null;
  }

  @NotNull
  public static Map<String, String> getParameters() {
    init();
    return Collections.unmodifiableMap(ourParameters);
  }
}
