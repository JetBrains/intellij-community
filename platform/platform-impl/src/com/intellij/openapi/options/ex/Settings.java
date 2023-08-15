// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public abstract class Settings {
  public static final DataKey<Settings> KEY = DataKey.create("settings.editor");

  private final List<? extends ConfigurableGroup> myGroups;
  private final Map<UnnamedConfigurable, ConfigurableWrapper> myMap = new IdentityHashMap<>();

  protected Settings(@NotNull List<? extends ConfigurableGroup> groups) {
    myGroups = groups;
  }

  public final @Nullable <T extends Configurable> T find(@NotNull Class<T> type) {
    return unwrap(ConfigurableVisitor.findByType(type, myGroups), type);
  }

  public final @Nullable Configurable find(@NotNull String id) {
    return unwrap(ConfigurableVisitor.findById(id, myGroups), Configurable.class);
  }

  public final @NotNull ActionCallback select(Configurable configurable) {
    return configurable == null ? ActionCallback.REJECTED : Promises.toActionCallback(selectImpl(choose(configurable, myMap.get(configurable))));
  }

  public final @NotNull ActionCallback select(Configurable configurable, String option) {
    ActionCallback callback = select(configurable);
    if (option != null && configurable instanceof SearchableConfigurable) {
      Runnable runnable = ((SearchableConfigurable)configurable).enableSearch(option);
      callback.doWhenDone(() -> {
        if (runnable != null) {
          runnable.run();
        }
        else {
          setSearchText(option);
        }
      });
    }
    return callback;
  }

  protected abstract @NotNull Promise<? super Object> selectImpl(Configurable configurable);

  public final @Nullable Configurable getConfigurableWithInitializedUiComponent(@NotNull String configurableId,
                                                                                boolean initializeUiComponentIfNotYet) {
    Configurable c = find(configurableId);
    if (c == null) return null;

    Configurable configurable = choose(c, myMap.get(c));
    return getConfigurableWithInitializedUiComponentImpl(configurable, initializeUiComponentIfNotYet);
  }

  protected abstract Configurable getConfigurableWithInitializedUiComponentImpl(@NotNull Configurable configurable,
                                                                                boolean initializeUiComponentIfNotYet);

  public final void checkModified(@NotNull String configurableId) {
    Configurable c = find(configurableId);
    if (c == null) return;

    Configurable configurable = choose(c, myMap.get(c));
    checkModifiedImpl(configurable);
  }

  protected abstract void checkModifiedImpl(@NotNull Configurable configurable);

  protected abstract void setSearchText(String option);

  private <T extends Configurable> T unwrap(Configurable configurable, Class<T> type) {
    T result = ConfigurableWrapper.cast(type, configurable);
    if (result != null && configurable instanceof ConfigurableWrapper) {
      myMap.put(result, (ConfigurableWrapper)configurable);
    }
    return result;
  }

  private static Configurable choose(Configurable configurable, Configurable variant) {
    return variant != null ? variant : configurable;
  }

  /**
   * Used to handle programmatic settings changes when no UI events are sent.
   */
  public void revalidate() {}

  public void reload() {
    myMap.clear();
  }
}
