// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.FileChannelInterruptsRetryer.FileChannelIdempotentOperation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
public interface Resilient {
  /** Executes idempotent (=safely repeatable) operation on the channel, retrying the operation until succeeded */
  <T> T executeOperation(@NotNull FileChannelIdempotentOperation<T> operation) throws IOException;
}
