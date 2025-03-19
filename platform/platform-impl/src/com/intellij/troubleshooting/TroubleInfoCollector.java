// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.troubleshooting;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this interface and register the implementation as com.intellij.troubleInfoCollector extension
 * to see a result of {@link #collectInfo} in "Help|Collect Troubleshooting Information" dialog.
 * <p>
 * Implement toString() for better presentation in {@link com.intellij.troubleshooting.ui.CollectTroubleshootingInformationDialog}
 */
public interface TroubleInfoCollector {
  ExtensionPointName<TroubleInfoCollector> EP_SETTINGS = ExtensionPointName.create("com.intellij.troubleInfoCollector");

  @NotNull
  String collectInfo(@NotNull Project project);
}
