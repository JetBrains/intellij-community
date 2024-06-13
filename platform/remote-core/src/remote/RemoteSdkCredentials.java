// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import org.jetbrains.annotations.NotNull;

public interface RemoteSdkCredentials extends MutableRemoteCredentials, RemoteSdkProperties {
  @NotNull String getFullInterpreterPath();
}
