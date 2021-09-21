// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress;

import org.jetbrains.annotations.ApiStatus.Internal;

/**
 * This has to be an inheritor of PCE, so that the code expecting PCE from ProgressManager#checkCanceled will continue to work.
 */
@Internal
public final class JobCanceledException extends ProcessCanceledException {

  public JobCanceledException() {
    super();
  }
}
