// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.extensions.ExtensionPointName;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBProtocolCommand {
  public static final String PROTOCOL = JetBrainsProtocolHandler.PROTOCOL;
  public static final String FRAGMENT_PARAM_NAME = JetBrainsProtocolHandler.FRAGMENT_PARAM_NAME;

  private static final ExtensionPointName<JBProtocolCommand> EP_NAME = new ExtensionPointName<>("com.intellij.jbProtocolCommand");

  private final String myCommand;

  public JBProtocolCommand(@NotNull String command) {
    myCommand = command;
  }

  @NotNull
  public final String getCommandName() {
    return myCommand;
  }

  public abstract void perform(String target, @NotNull Map<String, String> parameters);

  private void perform(@Nullable String target, Map<String, List<String>> parameters, @Nullable String fragment) {
    Map<String, String> simpleParameters = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
      List<String> list = entry.getValue();
      simpleParameters.put(entry.getKey(), list.isEmpty() ? "" : list.get(list.size() - 1));
    }
    simpleParameters.put(FRAGMENT_PARAM_NAME, fragment);
    perform(target, simpleParameters);
  }

  @ApiStatus.Internal
  public static void execute(@NotNull String url) {
    if (!url.startsWith(PROTOCOL)) throw new IllegalArgumentException(url);

    String query = url.substring(PROTOCOL.length());
    QueryStringDecoder decoder = new QueryStringDecoder(query);
    List<String> parts = Arrays.asList(decoder.path().split("/"));
    if (parts.size() < 2) throw new IllegalArgumentException(url);  // expecting at least platform prefix and a command name

    String commandName = parts.get(1);
    for (JBProtocolCommand command : EP_NAME.getIterable()) {
      if (command.getCommandName().equals(commandName)) {
        String target = parts.size() > 2 ? parts.get(2) : null;

        int fragmentStart = query.lastIndexOf('#');
        String fragment = fragmentStart > 0 ? query.substring(fragmentStart + 1) : null;

        command.perform(target, decoder.parameters(), fragment);

        break;
      }
    }
  }

  @Deprecated
  @SuppressWarnings("ALL")
  public static void handleCurrentCommand() {
    String commandName = JetBrainsProtocolHandler.getCommand();
    for (JBProtocolCommand command : EP_NAME.getIterable()) {
      if (command.getCommandName().equals(commandName)) {
        try {
          command.perform(JetBrainsProtocolHandler.getMainParameter(), JetBrainsProtocolHandler.getParameters());
        }
        finally {
          JetBrainsProtocolHandler.clear();
        }
      }
    }
  }
}
