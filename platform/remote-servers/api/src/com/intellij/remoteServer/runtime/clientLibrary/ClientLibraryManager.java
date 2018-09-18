/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remoteServer.runtime.clientLibrary;

import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.EventListener;
import java.util.List;

/**
 * @author nik
 */
public abstract class ClientLibraryManager {
  @NotNull
  public static ClientLibraryManager getInstance() {
    return ServiceManager.getService(ClientLibraryManager.class);
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
