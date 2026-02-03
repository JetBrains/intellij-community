// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

public final class CompactGroupHelper {

  public static @Unmodifiable @NotNull List<String> pathToPathList(@NotNull String parentTextOrig) {
    if (parentTextOrig.contains("!")) {
      int index = parentTextOrig.indexOf('!');
      String zip = parentTextOrig.substring(1, index + 1);
      String rest = parentTextOrig.substring(index + 1);
      List<String> subPaths = new ArrayList<>();
      subPaths.add(zip);
      subPaths.addAll(ContainerUtil.filter(rest.split("/"), s -> !s.isEmpty()));
      return subPaths;
    }
    return ContainerUtil.filter(parentTextOrig.split("/"), s -> !s.isEmpty());
  }
}
