// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface Supplier<T> {
  T get();
}
