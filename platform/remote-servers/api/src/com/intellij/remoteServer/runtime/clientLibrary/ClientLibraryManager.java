// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.runtime.clientLibrary;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class ClientLibraryManager {
  public static @NotNull ClientLibraryManager getInstance() {
    return ApplicationManager.getApplication().getService(ClientLibraryManager.class);
  }

  public abstract @NotNull List<File> download(@NotNull ClientLibraryDescription libraryDescription) throws IOException;
}
