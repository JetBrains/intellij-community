// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.http;

import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class HttpFileSystemImpl extends HttpFileSystemBase {
  public HttpFileSystemImpl() {
    super(URLUtil.HTTP_PROTOCOL);
  }

  public static HttpFileSystemImpl getInstanceImpl() {
    return (HttpFileSystemImpl)getInstance();
  }

}
