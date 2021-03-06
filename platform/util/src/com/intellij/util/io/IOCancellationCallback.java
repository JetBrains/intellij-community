// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;

/**
 * Check whether the current process should be terminated to avoid long IO-operation
 * and throws cancellation exception if it does.
 */
@FunctionalInterface
@ApiStatus.Experimental
public interface IOCancellationCallback {
  void checkCancelled();
}
