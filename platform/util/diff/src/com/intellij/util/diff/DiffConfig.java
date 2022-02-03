// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff;

import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

@ApiStatus.Internal
public class DiffConfig {
  private static final @NonNls String DIFF_DELTA_THRESHOLD_SIZE_KEY = "idea.diff.delta.threshold.size";
  private static final @NonNls String DIFF_DELTA_PATIENCE_ALG_KEY = "idea.diff.force.patience.alg";

  public static final boolean USE_PATIENCE_ALG = SystemProperties.getBooleanProperty(DIFF_DELTA_PATIENCE_ALG_KEY, false);
  public static final boolean USE_GREEDY_MERGE_MAGIC_RESOLVE = false;
  public static final int DELTA_THRESHOLD_SIZE = SystemProperties.getIntProperty(DIFF_DELTA_THRESHOLD_SIZE_KEY, 20000);
  public static final int MAX_BAD_LINES = 3; // Do not try to compare lines by-word after that many prior failures.
  public static final int UNIMPORTANT_LINE_CHAR_COUNT = 3; // Deprioritize short lines
}
