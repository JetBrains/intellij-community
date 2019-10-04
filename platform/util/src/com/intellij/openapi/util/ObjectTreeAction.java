// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

interface ObjectTreeAction<T> {

  void execute(@NotNull T each);

  void beforeTreeExecution(@NotNull T parent);

}
