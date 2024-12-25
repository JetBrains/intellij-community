// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;

public interface VcsFileContent {
  byte @Nullable [] loadContent() throws IOException, VcsException;

  /**
   * @deprecated Use {@link #loadContent()} instead
   */
  @Deprecated
  byte @Nullable [] getContent() throws IOException, VcsException;


  /**
   * @return the default charset to be used if detection by content fails. If null, the encoding from project settings will be used.
   */
  default @Nullable Charset getDefaultCharset() {
    return null;
  }
}
