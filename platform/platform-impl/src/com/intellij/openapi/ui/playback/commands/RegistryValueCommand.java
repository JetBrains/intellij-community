// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class RegistryValueCommand extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "set";

  public RegistryValueCommand(String text, int line) {
    super(text, line);
  }

  @Override
  protected Promise<Object> _execute(PlaybackContext context) {
    final String[] keyValue = getText().substring(PREFIX.length()).trim().split("=");
    if (keyValue.length != 2) {
      context.error("Expected expresstion: " + PREFIX + " key=value", getLine());
      return Promises.rejectedPromise();
    }

    final String key = keyValue[0];
    final String value = keyValue[1];

    context.storeRegistryValue(key);

    Registry.get(key).setValue(value);

    return Promises.resolvedPromise();
  }
}
