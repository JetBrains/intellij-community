// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.attach;

import com.intellij.execution.process.ProcessInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface XAttachProcessPresentationGroup extends XAttachPresentationGroup<ProcessInfo> {
  @Override
  default int compare(@NotNull ProcessInfo a, @NotNull ProcessInfo b) {
    return a.getPid() - b.getPid();
  }
}
