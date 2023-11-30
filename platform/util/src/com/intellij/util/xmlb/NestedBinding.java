// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.serialization.MutableAccessor;
import org.jetbrains.annotations.NotNull;

public interface NestedBinding extends Binding {
  NestedBinding[] EMPTY_ARRAY = {};
  @NotNull MutableAccessor getAccessor();
}
