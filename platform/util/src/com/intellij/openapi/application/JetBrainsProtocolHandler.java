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
package com.intellij.openapi.application;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
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
  private static final Map<String, String> ourParameters = new HashMap<String, String>(0);
  private static boolean initialized = false;

  public static void processJetBrainsLauncherParameters(String url) {
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
          } else {
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

  public static void clear() {
    ourCommand = null;
  }

  public static Map<String, String> getParameters() {
    init();
    return Collections.unmodifiableMap(ourParameters);
  }
}
