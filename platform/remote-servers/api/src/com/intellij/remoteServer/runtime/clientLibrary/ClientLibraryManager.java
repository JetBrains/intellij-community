// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.runtime.clientLibrary;

import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.EventListener;
import java.util.List;

public abstract class ClientLibraryManager {
  @NotNull
  public static ClientLibraryManager getInstance() {
    return ApplicationManager.getApplication().getService(ClientLibraryManager.class);
  }

  @NotNull
  public abstract List<File> download(@NotNull ClientLibraryDescription libraryDescription) throws IOException;

  /**
   * @deprecated to be removed with obsolete cloud implementations
   */
  @Deprecated
  @NotNull
  public abstract List<File> getLibraries(@NotNull ClientLibraryDescription description);

  /**
   * @deprecated to be removed with obsolete cloud implementations
   */
  @Deprecated
  public abstract void addListener(@NotNull CloudClientLibraryManagerListener listener, @NotNull Disposable disposable);

  /**
   * @deprecated will be pushed down to implementation when obsolete cloud implementations are removed
   */
  @Deprecated
  public abstract boolean isDownloaded(@NotNull ClientLibraryDescription description);

  /**
   * @deprecated to be removed with obsolete cloud implementations
   */
  @Deprecated
  public abstract void checkConfiguration(@NotNull ClientLibraryDescription description, @Nullable Project project,
                                          @Nullable JComponent component) throws RuntimeConfigurationError;

  /**
   * @deprecated will be pushed down to implementation when obsolete cloud implementations are removed
   */
  @Deprecated
  public abstract void download(@NotNull ClientLibraryDescription description, @Nullable Project project, @Nullable JComponent component);

  public interface CloudClientLibraryManagerListener extends EventListener {
    void downloaded();
  }
}
