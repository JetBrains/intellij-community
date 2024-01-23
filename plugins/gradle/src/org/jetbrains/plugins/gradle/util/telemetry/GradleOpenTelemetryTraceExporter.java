// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.telemetry;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class GradleOpenTelemetryTraceExporter {

  private static final Logger LOG = Logger.getInstance(GradleOpenTelemetryTraceExporter.class);

  public static void export(@NotNull URI targetUri, byte[] binaryTraces) {
    try {
      HttpClient.newHttpClient()
        .send(HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofByteArray(binaryTraces))
                .uri(targetUri)
                .header("Content-Type", "application/x-protobuf")
                .build(),
              HttpResponse.BodyHandlers.discarding());
    }
    catch (Exception e) {
      LOG.error("Unable to upload performance traces to the OTLP server", e);
    }
  }
}
