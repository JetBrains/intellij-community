// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.runtime.clientLibrary;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class ClientLibraryManager {
  @NotNull
  public static ClientLibraryManager getInstance() {
    return ApplicationManager.getApplication().getService(ClientLibraryManager.class);
  }

  @NotNull
  public abstract List<File> download(@NotNull ClientLibraryDescription libraryDescription) throws IOException;
}
