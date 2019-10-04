// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Sergey.Malenkov
 */
public abstract class ConfigurableVisitor {
  public static final ConfigurableVisitor ALL = new ConfigurableVisitor() {
    @Override
    protected boolean accept(@NotNull Configurable configurable) {
      return true;
    }
  };

  protected abstract boolean accept(@NotNull Configurable configurable);

  @Nullable
  public final Configurable find(@NotNull ConfigurableGroup... groups) {
    return find(Arrays.asList(groups));
  }

  @Nullable
  public final Configurable find(@NotNull List<? extends ConfigurableGroup> groups) {
    for (ConfigurableGroup group : groups) {
      Configurable result = find(group.getConfigurables());
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  public final Configurable find(@NotNull Configurable... configurables) {
    for (Configurable configurable : configurables) {
      if (accept(configurable)) {
        return configurable;
      }
    }
    for (Configurable configurable : configurables) {
      if (configurable instanceof Configurable.Composite) {
        Configurable.Composite composite = (Configurable.Composite)configurable;
        Configurable result = find(composite.getConfigurables());
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  public final List<Configurable> findAll(@NotNull List<? extends ConfigurableGroup> groups) {
    List<Configurable> list = new ArrayList<>();
    for (ConfigurableGroup group : groups) {
      add(list, group.getConfigurables());
    }
    return list;
  }

  public final List<Configurable> findAll(@NotNull Configurable... configurables) {
    List<Configurable> list = new ArrayList<>();
    add(list, configurables);
    return list;
  }

  private void add(List<? super Configurable> list, Configurable... configurables) {
    for (Configurable configurable : configurables) {
      if (accept(configurable)) {
        list.add(configurable);
      }
      if (configurable instanceof Configurable.Composite) {
        Configurable.Composite composite = (Configurable.Composite)configurable;
        add(list, composite.getConfigurables());
      }
    }
  }

  public static final class ByID extends ConfigurableVisitor {
    private final String myID;

    public ByID(@NotNull String id) {
      myID = id;
    }

    @Override
    protected boolean accept(@NotNull Configurable configurable) {
      return myID.equals(getID(configurable));
    }

    public static String getID(Configurable configurable) {
      return configurable instanceof SearchableConfigurable
             ? ((SearchableConfigurable)configurable).getId()
             : configurable.getClass().getName();
    }
  }

  public static final class ByName extends ConfigurableVisitor {
    private final String myName;

    public ByName(@NotNull String name) {
      myName = name;
    }

    @Override
    protected boolean accept(@NotNull Configurable configurable) {
      return myName.equals(configurable.getDisplayName());
    }
  }

  public static final class ByType extends ConfigurableVisitor {
    private final Class<? extends Configurable> myType;

    public ByType(@NotNull Class<? extends Configurable> type) {
      myType = type;
    }

    @Override
    protected boolean accept(@NotNull Configurable configurable) {
      return ConfigurableWrapper.cast(myType, configurable) != null;
    }
  }
}
