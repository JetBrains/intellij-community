// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.PerformInBackgroundOption;

/**
 * @deprecated use nothing or {@link PerformInBackgroundOption#ALWAYS_BACKGROUND} instead
 */
@Deprecated(forRemoval = true)
public class BackgroundFromStartOption {

  public static PerformInBackgroundOption getInstance() {
    return PerformInBackgroundOption.ALWAYS_BACKGROUND;
  }
}
