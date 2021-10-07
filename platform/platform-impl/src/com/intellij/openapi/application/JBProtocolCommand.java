// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

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

  public final @NotNull String getCommandName() {
    return myCommand;
  }

  /** @deprecated please implement {@link #perform(String, Map, String)} instead */
  @Deprecated(forRemoval = true)
  public void perform(String target, @NotNull Map<String, String> parameters) {
    throw PluginException.createByClass(new UnsupportedOperationException(), getClass());
  }

  /**
   * The method should return a future with the command execution result - {@code null} when successful,
   * sentence-capitalized localized string in case of an error (see {@code "ide.protocol.*"} strings in {@code IdeBundle}).
   */
  public @NotNull Future<@Nullable @DialogMessage String> perform(@Nullable String target, @NotNull Map<String, String> parameters, @Nullable String fragment) {
    Map<String, String> simpleParameters = new LinkedHashMap<>(parameters);
    simpleParameters.put(JetBrainsProtocolHandler.FRAGMENT_PARAM_NAME, fragment);
    perform(target, simpleParameters);
    return CompletableFuture.completedFuture(null);
  }

  @ApiStatus.Internal
  public static @NotNull Future<@Nullable @DialogMessage String> execute(@NotNull String url) {
    if (!url.startsWith(PROTOCOL)) throw new IllegalArgumentException(url);

    String query = url.substring(PROTOCOL.length());
    QueryStringDecoder decoder = new QueryStringDecoder(query);
    String[] parts = decoder.path().split("/");
    if (parts.length < 2) throw new IllegalArgumentException(url);  // expecting at least a platform prefix and a command name

    String commandName = parts[1];
    for (JBProtocolCommand command : EP_NAME.getIterable()) {
      if (command.getCommandName().equals(commandName)) {
        String target = parts.length > 2 ? parts[2] : null;

        Map<String, String> parameters = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : decoder.parameters().entrySet()) {
          List<String> list = entry.getValue();
          parameters.put(entry.getKey(), list.isEmpty() ? "" : list.get(list.size() - 1));
        }

        int fragmentStart = query.lastIndexOf('#');
        String fragment = fragmentStart > 0 ? query.substring(fragmentStart + 1) : null;

        return command.perform(target, Collections.unmodifiableMap(parameters), fragment);
      }
    }

    return CompletableFuture.completedFuture(IdeBundle.message("ide.command.line.unknown.command", commandName));
  }

  @Deprecated
  @SuppressWarnings("ALL")
  public static void handleCurrentCommand() {
    String commandName = JetBrainsProtocolHandler.getCommand();
    for (JBProtocolCommand command : EP_NAME.getIterable()) {
      if (command.getCommandName().equals(commandName)) {
        try {
          Map<String, String> parameters = JetBrainsProtocolHandler.getParameters();
          command.perform(JetBrainsProtocolHandler.getMainParameter(), parameters, parameters.get(FRAGMENT_PARAM_NAME));
        }
        finally {
          JetBrainsProtocolHandler.clear();
        }
      }
    }
  }
}
