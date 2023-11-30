// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Predicates;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class ConfigurableVisitor implements Predicate<Configurable> {
  public static final Predicate<Configurable> ALL = Predicates.alwaysTrue();

  @Override
  public boolean test(Configurable configurable) {
    return accept(configurable);
  }

  protected abstract boolean accept(@NotNull Configurable configurable);

  public final @Nullable Configurable find(ConfigurableGroup @NotNull ... groups) {
    return find(this, Arrays.asList(groups));
  }

  public static @Nullable Configurable findById(@NotNull String id, @NotNull List<? extends ConfigurableGroup> groups) {
    return find(configurable -> id.equals(getId(configurable)), groups);
  }

  public static @Nullable Configurable findByType(@NotNull Class<? extends Configurable> type, @NotNull List<? extends ConfigurableGroup> groups) {
    return find(configurable -> ConfigurableWrapper.cast(type, configurable) != null, groups);
  }

  public static @Nullable Configurable find(@NotNull Predicate<? super Configurable> visitor, @NotNull List<? extends ConfigurableGroup> groups) {
    for (ConfigurableGroup group : groups) {
      Configurable result = find(visitor, group.getConfigurables());
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  public static @Nullable Configurable find(@NotNull Predicate<? super Configurable> visitor, Configurable @NotNull [] configurables) {
    for (Configurable configurable : configurables) {
      if (visitor.test(configurable)) {
        return configurable;
      }
    }

    for (Configurable configurable : configurables) {
      if (configurable instanceof Configurable.Composite composite) {
        Configurable result = find(visitor, composite.getConfigurables());
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  public static @NotNull List<Configurable> findAll(@NotNull Predicate<? super Configurable> visitor, @NotNull List<? extends ConfigurableGroup> groups) {
    List<Configurable> list = new ArrayList<>();
    Consumer<Configurable> consumer = configurable -> {
      if (visitor.test(configurable)) {
        list.add(configurable);
      }
    };
    for (ConfigurableGroup group : groups) {
      collect(consumer, group.getConfigurables());
    }
    return list;
  }

  @ApiStatus.Internal
  public static void collect(@NotNull Consumer<? super Configurable> visitor, Configurable @NotNull [] configurables) {
    for (Configurable configurable : configurables) {
      visitor.accept(configurable);
      if (configurable instanceof Configurable.Composite) {
        collect(visitor, ((Configurable.Composite)configurable).getConfigurables());
      }
    }
  }

  public static @NotNull String getId(@NotNull Configurable configurable) {
    return configurable instanceof SearchableConfigurable
           ? ((SearchableConfigurable)configurable).getId()
           : configurable.getClass().getName();
  }
}
