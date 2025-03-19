// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NonNls;

public final class HttpsFileSystem extends HttpFileSystemBase {
  public static final @NonNls String HTTPS_PROTOCOL = "https";

  public HttpsFileSystem() {
    super(HTTPS_PROTOCOL);
  }

  public static HttpsFileSystem getHttpsInstance() {
    return (HttpsFileSystem)VirtualFileManager.getInstance().getFileSystem(HTTPS_PROTOCOL);
  }

}
