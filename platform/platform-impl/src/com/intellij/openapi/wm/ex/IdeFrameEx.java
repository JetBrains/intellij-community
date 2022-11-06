// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@ApiStatus.Internal
public interface IdeFrameEx extends IdeFrame {
  void setFileTitle(@Nullable String fileTitle, @Nullable Path ioFile);

  @NotNull
  CompletableFuture<?> toggleFullScreen(boolean state);

  @Nullable
  JComponent getNorthExtension(@NotNull String key);
}
