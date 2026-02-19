// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.socketConnection;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class RequestResponseExternalizerFactory<Request extends AbstractRequest, Response extends AbstractResponse> {
  public abstract @NotNull RequestWriter<Request> createRequestWriter(@NotNull OutputStream output) throws IOException;

  public abstract @NotNull ResponseReader<Response> createResponseReader(@NotNull InputStream input) throws IOException;
}
