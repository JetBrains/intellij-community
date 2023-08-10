// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface VcsAwareCheckoutListener {
  ExtensionPointName<VcsAwareCheckoutListener> EP_NAME = new ExtensionPointName<>("com.intellij.vcsAwareCheckoutListener");

  @RequiresBackgroundThread
  boolean processCheckedOutDirectory(Project project, @NotNull Path directory, VcsKey vcsKey);
}
