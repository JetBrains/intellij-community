// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.CommandLineProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * @author Dennis.Ushakov
 */
public class CustomProtocolHandler {
  public static final String LINE_NUMBER_ARG_NAME = "--line";

  private static final Logger LOG = Logger.getInstance(CustomProtocolHandler.class);

  public boolean openLink(@NotNull URI uri) {
    LOG.info("CustomProtocolHandler.openLink");
    final List<String> args = getOpenArgs(uri);
    return !args.isEmpty() && CommandLineProcessor.processExternalCommandLine(args, null).getFirst() != null;
  }

  @NotNull
  public List<String> getOpenArgs(@NotNull URI uri) {
    Map<String, List<String>> parameters = new QueryStringDecoder(uri).parameters();
    String file = ContainerUtil.getFirstItem(parameters.get("file"));
    String line = ContainerUtil.getFirstItem(parameters.get("line"));

    List<String> args = new SmartList<>();
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
