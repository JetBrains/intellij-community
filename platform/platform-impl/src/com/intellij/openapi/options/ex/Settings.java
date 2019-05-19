// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

/**
 * @author Sergey.Malenkov
 */
public abstract class Settings {
  public static final DataKey<Settings> KEY = DataKey.create("settings.editor");

  private final List<ConfigurableGroup> myGroups;
  private final IdentityHashMap<UnnamedConfigurable, ConfigurableWrapper>
    myMap = new IdentityHashMap<>();

  protected Settings(@NotNull List<ConfigurableGroup> groups) {
    myGroups = groups;
  }

  @Nullable
  public final <T extends Configurable> T find(@NotNull Class<T> type) {
    return unwrap(new ConfigurableVisitor.ByType(type).find(myGroups), type);
  }

  @Nullable
  public final Configurable find(@NotNull String id) {
    return unwrap(new ConfigurableVisitor.ByID(id).find(myGroups), Configurable.class);
  }

  @NotNull
  public final ActionCallback select(Configurable configurable) {
    return configurable == null ? ActionCallback.REJECTED : Promises.toActionCallback(selectImpl(choose(configurable, myMap.get(configurable))));
  }

  @NotNull
  public final ActionCallback select(Configurable configurable, String option) {
    ActionCallback callback = select(configurable);
    if (option != null && configurable instanceof SearchableConfigurable) {
      SearchableConfigurable searchable = (SearchableConfigurable)configurable;
      Runnable search = searchable.enableSearch(option);
      if (search != null) callback.doWhenDone(search);
    }
    return callback;
  }

  @NotNull
  protected abstract Promise<? super Object> selectImpl(Configurable configurable);

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
}
