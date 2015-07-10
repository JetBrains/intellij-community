/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class JetBrainsProtocolHandler {
  public static final String PROTOCOL = "jetbrains://";
  public static final String MAIN_PARAMETER = "JETBRAINS_PROTOCOL_HANDLER_MAIN_PARAMETER";
  public static final String COMMAND = "JETBRAINS_PROTOCOL_HANDLER_COMMAND";

  public static void processJetBrainsLauncherParameters(String url) {
    url = url.substring(PROTOCOL.length());
    String platformPrefix = url.substring(0, url.indexOf('/'));
    url = url.substring(platformPrefix.length() + 1);
    String command = url.substring(0, url.indexOf('/'));
    url = url.substring(command.length() + 1);
    List<String> strings = StringUtil.split(url, "?");
    String arg = strings.get(0);
    System.setProperty(MAIN_PARAMETER, arg);
    System.setProperty(COMMAND, command);

    if (strings.size() > 1) {
      List<String> keyValues = StringUtil.split(StringUtil.join(ContainerUtil.subList(strings, 1), "?"), "&");
      for (String keyValue : keyValues) {
        if (keyValue.contains("=")) {
          int ind = keyValue.indexOf('=');
          System.setProperty(keyValue.substring(0, ind), keyValue.substring(ind + 1));
        } else {
          System.setProperty(keyValue, "");
        }
      }
    }
  }

  @Nullable
  public static String getCommand() {
    return System.getProperty(COMMAND);
  }

  public static String getMainParameter() {
    return System.getProperty(MAIN_PARAMETER);
  }

  public static void clear() {
    System.setProperty(COMMAND, "");
  }
}
