// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBProtocolCommand {
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

  @Nullable
  public static JBProtocolCommand findCommand(@Nullable String commandName) {
    if (commandName != null) {
      for (JBProtocolCommand command : EP_NAME.getIterable()) {
        if (command.getCommandName().equals(commandName)) {
          return command;
        }
      }
    }
    return null;
  }

  public static void handleCurrentCommand() {
    JBProtocolCommand command = findCommand(JetBrainsProtocolHandler.getCommand());
    if (command != null) {
      try {
        command.perform(JetBrainsProtocolHandler.getMainParameter(), JetBrainsProtocolHandler.getParameters());
      }
      finally {
        JetBrainsProtocolHandler.clear();
      }
    }
  }
}
