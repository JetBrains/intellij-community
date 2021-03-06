// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.*;

/**
 * Only JDK classes should be used. This class is used in Main before bootstrap.
 */
public final class JetBrainsProtocolHandler {
  public static final String PROTOCOL = "jetbrains://";
  public static final @NonNls String FRAGMENT_PARAM_NAME = "__fragment";
  public static final String REQUIRED_PLUGINS_KEY = "idea.required.plugins.id";

  private static String ourMainParameter = null;
  private static String ourCommand = null;
  private static Map<String, String> ourParameters = Collections.emptyMap();
  private static boolean initialized = false;

  public static void processJetBrainsLauncherParameters(@NotNull String url) {
    System.setProperty(JetBrainsProtocolHandler.class.getName(), url);

    // parse without protocol, otherwise first path component will be considered as host
    URI uri = URI.create(url.substring(PROTOCOL.length()));

    String path = uri.getPath();
    List<String> urlParts = Arrays.asList(path.split("/"));
    // expect at least platform prefix and command name
    if (urlParts.size() < 2) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.print("Wrong URL: " + PROTOCOL + url);
      return;
    }

    ourCommand = urlParts.get(1);
    ourMainParameter = urlParts.size() > 2 ? urlParts.get(2) : null;
    Map<String, String> parameters = new HashMap<>();
    computeParameters(uri.getRawQuery(), parameters);
    parameters.put(FRAGMENT_PARAM_NAME, uri.getFragment());
    ourParameters = Collections.unmodifiableMap(parameters);
    initialized = true;
  }

  // well, Netty cannot be added as dependency and so, QueryStringDecoder cannot be used
  private static void computeParameters(@Nullable String rawQuery, Map<String, String> parameters) {
    if (rawQuery == null || rawQuery.isEmpty()) {
      return;
    }

    for (String keyValue : rawQuery.split("&")) {
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

  public static @Nullable @NonNls String getCommand() {
    init();
    return ourCommand;
  }

  private static void init() {
    String property = initialized ? null : System.getProperty(JetBrainsProtocolHandler.class.getName());
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

  public static @NotNull Map<String, String> getParameters() {
    init();
    return ourParameters;
  }

  public static boolean isShutdownCommand() {
    return "shutdown".equals(getCommand());
  }

  public static String @NotNull [] checkForJetBrainsProtocolCommand(String @NotNull [] args) {
    String property = System.getProperty(JetBrainsProtocolHandler.class.getName());
    return property != null ? new String[]{property} : args;
  }
}