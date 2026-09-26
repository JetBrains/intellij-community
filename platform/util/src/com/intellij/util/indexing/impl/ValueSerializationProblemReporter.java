// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import org.jetbrains.annotations.NotNull;

public interface ValueSerializationProblemReporter {
  void reportProblem(@NotNull Exception exception);
}
