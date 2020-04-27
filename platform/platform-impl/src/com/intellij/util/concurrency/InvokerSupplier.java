// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import org.jetbrains.annotations.NotNull;

public interface InvokerSupplier {
  /**
   * @return preferable invoker to be used to access the supplier
   */
  @NotNull
  Invoker getInvoker();
}
