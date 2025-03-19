// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.runtime.clientLibrary;

import org.jetbrains.annotations.NotNull;

import java.net.URL;

public class ClientLibraryDescription {
  private final String myId;
  private final @NotNull URL myDescriptionUrl;

  public ClientLibraryDescription(@NotNull String id, @NotNull URL descriptionUrl) {
    myId = id;
    myDescriptionUrl = descriptionUrl;
  }

  public final String getId() {
    return myId;
  }

  public @NotNull URL getDescriptionUrl() {
    return myDescriptionUrl;
  }
}
