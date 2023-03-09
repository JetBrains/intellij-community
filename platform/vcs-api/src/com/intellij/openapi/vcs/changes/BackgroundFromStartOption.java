// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.PerformInBackgroundOption;

/**
 * @deprecated use nothing or {@link PerformInBackgroundOption#ALWAYS_BACKGROUND} instead
 */
@Deprecated(forRemoval = true)
public final class BackgroundFromStartOption {

  public static PerformInBackgroundOption getInstance() {
    return PerformInBackgroundOption.ALWAYS_BACKGROUND;
  }
}
