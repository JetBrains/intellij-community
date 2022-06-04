// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NlsContexts.NotificationContent;
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
  public static final String SCHEME = "jetbrains";
  public static final String FRAGMENT_PARAM_NAME = "__fragment";

  private static final ExtensionPointName<JBProtocolCommand> EP_NAME = new ExtensionPointName<>("com.intellij.jbProtocolCommand");

  private final String myCommand;

  public JBProtocolCommand(@NotNull String command) {
    myCommand = command;
  }

  /** @deprecated please implement {@link #perform(String, Map, String)} instead */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  public void perform(String target, @NotNull Map<String, String> parameters) {
    throw PluginException.createByClass(new UnsupportedOperationException(), getClass());
  }

  /**
   * The method should return a future with the command execution result - {@code null} when successful,
   * sentence-capitalized localized string in case of an error (see {@code "ide.protocol.*"} strings in {@code IdeBundle}).
   *
   * @see #parameter(Map, String)
   */
  public @NotNull Future<@Nullable @NotificationContent String> perform(@Nullable String target, @NotNull Map<String, String> parameters, @Nullable String fragment) {
    Map<String, String> simpleParameters = new LinkedHashMap<>(parameters);
    simpleParameters.put(FRAGMENT_PARAM_NAME, fragment);
    perform(target, simpleParameters);
    return CompletableFuture.completedFuture(null);
  }

  protected @NotNull String parameter(@NotNull Map<String, String> parameters, @NotNull String name) {
    String value = parameters.get(name);
    if (value == null || value.isBlank()) throw new IllegalArgumentException(IdeBundle.message("jb.protocol.parameter.missing", name));
    return value;
  }

  @ApiStatus.Internal
  public static @NotNull Future<@Nullable @NotificationContent String> execute(@NotNull String query) {
    QueryStringDecoder decoder = new QueryStringDecoder(query);
    String[] parts = decoder.path().split("/");
    if (parts.length < 2) throw new IllegalArgumentException(query);  // expected: at least a platform prefix and a command name

    String commandName = parts[1];
    for (JBProtocolCommand command : EP_NAME.getIterable()) {
      if (command.myCommand.equals(commandName)) {
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

    return CompletableFuture.completedFuture(IdeBundle.message("jb.protocol.unknown.command", commandName));
  }
}
