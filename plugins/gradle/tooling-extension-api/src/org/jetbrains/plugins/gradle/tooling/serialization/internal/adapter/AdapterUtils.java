// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter;

import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class AdapterUtils {
  @Contract("null -> null;!null -> !null;")
  @Nullable
  static <T> ImmutableDomainObjectSet<T> wrap(@Nullable Iterable<? extends T> children) {
    if (children == null) return null;
    return children instanceof ImmutableDomainObjectSet ? (ImmutableDomainObjectSet<T>)children : ImmutableDomainObjectSet.of(children);
  }
}
