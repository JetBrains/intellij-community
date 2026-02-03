// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.utils;

import com.intellij.xdebugger.memory.ui.ReferenceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@FunctionalInterface
public interface InstancesProvider {
  @NotNull
  List<ReferenceInfo> getInstances(int limit);
}
