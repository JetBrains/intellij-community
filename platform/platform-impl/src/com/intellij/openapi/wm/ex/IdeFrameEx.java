// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.nio.file.Path;

@ApiStatus.Internal
public interface IdeFrameEx extends IdeFrame {
  void setFileTitle(@Nullable String fileTitle, @Nullable Path ioFile);

  @NotNull
  Promise<?> toggleFullScreen(boolean state);

  @Nullable
  IdeRootPaneNorthExtension getNorthExtension(String key);
}
