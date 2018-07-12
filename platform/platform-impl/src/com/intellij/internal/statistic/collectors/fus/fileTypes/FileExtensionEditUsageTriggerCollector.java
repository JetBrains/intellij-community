// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.service.fus.collectors.ProjectUsageTriggerCollector;
import org.jetbrains.annotations.NotNull;

public class FileExtensionEditUsageTriggerCollector extends ProjectUsageTriggerCollector {

  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.file.extensions.edit";
  }
}
