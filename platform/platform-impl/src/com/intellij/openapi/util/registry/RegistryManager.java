// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.MissingResourceException;

@State(name = "Registry", storages = @Storage("ide.general.xml"))
public final class RegistryManager implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(RegistryManager.class);

  @NotNull
  public static RegistryManager getInstance() {
    return ApplicationManager.getApplication().getService(RegistryManager.class);
  }

  public int intValue(@NotNull String key) throws MissingResourceException {
    return Registry.get(key).asInteger();
  }

  @Override
  public Element getState() {
    return Registry.getInstance().getState();
  }

  @Override
  public void noStateLoaded() {
    Registry.getInstance().markAsLoaded();
  }

  @Override
  public void loadState(@NotNull Element state) {
    Registry registry = Registry.getInstance();
    registry.loadState(state);
    log(registry);
  }

  private static void log(@NotNull Registry registry) {
    Map<String, String> userProperties = registry.getUserProperties();
    if (userProperties.size() <= (userProperties.containsKey("ide.firstStartup") ? 1 : 0)) {
      return;
    }

    String[] keys = ArrayUtilRt.toStringArray(userProperties.keySet());
    Arrays.sort(keys);
    StringBuilder builder = new StringBuilder("Registry values changed by user: ");
    for (String key : keys) {
      if ("ide.firstStartup".equals(key)) {
        continue;
      }

      builder.append(key).append(" = ").append(userProperties.get(key)).append(", ");
    }
    LOG.info(builder.substring(0, builder.length() - 2));
  }
}