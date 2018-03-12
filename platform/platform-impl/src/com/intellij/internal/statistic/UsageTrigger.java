// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@Deprecated // to be removed in 2018.2
public class UsageTrigger {

  public static void trigger(@NotNull @NonNls String feature) {}

  public static void trigger(@NotNull @NonNls String groupId, @NotNull @NonNls String feature) {}

  public static void triggerOnce(@NotNull @NonNls String feature) {}
}
