// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
// implementation of `copyOf` is allowed to not do copy - it can return the same map, read `copyOf` as `immutable`
public abstract class Java11Shim {
  @SuppressWarnings("StaticNonFinalField")
  public static @NotNull Java11Shim INSTANCE = new Java11Shim() {
    @Override
    public <K, V> Map<K, V> copyOf(Map<? extends K, ? extends V> map) {
      return Collections.unmodifiableMap(map);
    }

    @Override
    public <E> @NotNull Set<E> copyOf(Set<? extends E> collection) {
      return Collections.unmodifiableSet(collection);
    }

    @Override
    public <E> @NotNull List<E> copyOfCollection(Collection<? extends E> collection) {
      return Collections.unmodifiableList(new ArrayList<>(collection));
    }
  };

  public abstract <@NotNull K, @NotNull V> Map<K, V> copyOf(Map<? extends K, ? extends V> map);

  public abstract <@NotNull E> @NotNull Set<E> copyOf(Set<? extends E> collection);

  public abstract <@NotNull E> @NotNull List<E> copyOfCollection(Collection<? extends E> collection);
}
