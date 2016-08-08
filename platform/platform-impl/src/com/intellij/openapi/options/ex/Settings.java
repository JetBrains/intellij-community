/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.options.ex;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;

/**
 * @author Sergey.Malenkov
 */
public abstract class Settings {
  public static final DataKey<Settings> KEY = DataKey.create("settings.editor");

  private final ConfigurableGroup[] myGroups;
  private final IdentityHashMap<UnnamedConfigurable, ConfigurableWrapper>
    myMap = new IdentityHashMap<>();

  protected Settings(@NotNull ConfigurableGroup... groups) {
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
    return configurable != null
           ? selectImpl(choose(configurable, myMap.get(configurable)))
           : ActionCallback.REJECTED;
  }

  protected abstract ActionCallback selectImpl(Configurable configurable);

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
}
