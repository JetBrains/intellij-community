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
package com.intellij.ui;

import com.intellij.ide.CommandLineProcessor;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public abstract class CustomProtocolHandler {
  public static final ExtensionPointName<CustomProtocolHandler> EP_NAME = ExtensionPointName.create("com.intellij.customProtocolHandler");
  private final String myScheme;

  protected CustomProtocolHandler(String scheme) {
    myScheme = scheme;
  }

  public boolean openLink(@NotNull URI uri) {
    final List<String> args = getOpenArgs(uri);
    return !args.isEmpty() && CommandLineProcessor.processExternalCommandLine(args, null) != null;
  }

  @NotNull
  public List<String> getOpenArgs(URI uri) {
    final List<String> args = new ArrayList<String>();
    if (myScheme.equals(uri.getScheme())) {
      final String query = uri.getQuery();
      String file = null;
      String line = null;
      if (query != null) {
        for (String param : query.split("&")) {
          String[] pair = param.split("=");
          String key = decode(pair[0]);
          if (pair.length > 1) {
            if ("file".equals(key)) {
              file = decode(pair[1]);
            } else if ("line".equals(key)) {
              line = decode(pair[1]);
            }
          }
        }
      }
      if (file != null) {

        if (line != null) {
          args.add("--line");
          args.add(line);
        }
        args.add(file);
      }
    }
    return args;
  }

  private static String decode(String s) {
    try {
      return URLDecoder.decode(s, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      return null;
    }
  }
}
