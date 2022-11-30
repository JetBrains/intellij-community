// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface CheckoutListener {
  ExtensionPointName<CheckoutListener> EP_NAME = new ExtensionPointName<>("com.intellij.checkoutListener");
  ExtensionPointName<CheckoutListener> COMPLETED_EP_NAME = new ExtensionPointName<>("com.intellij.checkoutCompletedListener");

  /**
   * Executed in a pooled thread, not in EDT.
   */
  @RequiresBackgroundThread
  boolean processCheckedOutDirectory(@NotNull Project project, @NotNull Path directory);
}