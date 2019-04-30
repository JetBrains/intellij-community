// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    url = url.substring(PROTOCOL.length());
    List<String> urlParts = StringUtil.split(url, "/");
    if (urlParts.size() < 2) {
      System.err.print("Wrong URL: " + PROTOCOL + url);
      return;
    }

    String platformPrefix = urlParts.get(0);
    ourMainParameter = null;
    ourParameters.clear();
    ourCommand = urlParts.get(1);
    if (urlParts.size() > 2) {
      url = url.substring(platformPrefix.length() + 1 + ourCommand.length() + 1);
      List<String> strings = StringUtil.split(url, "?");
      ourMainParameter = strings.get(0);

      if (strings.size() > 1) {
        List<String> keyValues = StringUtil.split(StringUtil.join(ContainerUtil.subList(strings, 1), "?"), "&");
        for (String keyValue : keyValues) {
          if (keyValue.contains("=")) {
            int ind = keyValue.indexOf('=');
            String key = keyValue.substring(0, ind);
            String value = keyValue.substring(ind + 1);
            if (REQUIRED_PLUGINS_KEY.equals(key)) {
              System.setProperty(key, value);
            } else {
              ourParameters.put(key, value);
            }
          }
          else {
            ourParameters.put(keyValue, "");
          }
        }
      }
    }

    initialized = true;
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
