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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class CustomProtocolHandler {
  public static final String LINE_NUMBER_ARG_NAME = "--line";

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.CustomProtocolHandler");
  public boolean openLink(@NotNull URI uri) {
    LOG.info("CustomProtocolHandler.openLink");
    final List<String> args = getOpenArgs(uri);
    return !args.isEmpty() && CommandLineProcessor.processExternalCommandLine(args, null) != null;
  }

  @NotNull
  public List<String> getOpenArgs(URI uri) {
    final List<String> args = new ArrayList<>();
    final String query = uri.getQuery();
    String file = null;
    String line = null;
    if (query != null) {
      for (String param : query.split("&")) {
        String[] pair = param.split("=");
        String key = URLUtil.unescapePercentSequences(pair[0]);
        if (pair.length > 1) {
          if ("file".equals(key)) {
            file = URLUtil.unescapePercentSequences(pair[1]);
          } else if ("line".equals(key)) {
            line = URLUtil.unescapePercentSequences(pair[1]);
          }
        }
      }
    }

    if (file != null) {
      if (line != null) {
        args.add(LINE_NUMBER_ARG_NAME);
        args.add(line);
      }
      args.add(file);
    }
    return args;
  }
}
