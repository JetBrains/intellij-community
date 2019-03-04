// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.troubleshooting;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this interface and register the implementation as com.intellij.systemInfoCollector extension
 * to see result of {@link #collectInfo} in the "General" section
 * of the "Help|Collect Troubleshooting Information" dialog.
 */
public interface GeneralTroubleInfoCollector {
  ExtensionPointName<GeneralTroubleInfoCollector> EP_SETTINGS = ExtensionPointName.create("com.intellij.generalTroubleInfoCollector");

  @NotNull
  String getTitle();

  @NotNull
  String collectInfo(@NotNull Project project);
}