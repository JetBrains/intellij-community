// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.telemetry;

import com.intellij.util.ArrayUtilRt;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Collection;

public final class SpanDataSerializer {

  public static byte[] serialize(@NotNull Collection<SpanData> spanData, @NotNull GradleTelemetryFormat format) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      if (format == GradleTelemetryFormat.PROTOBUF) {
        serializeIntoProtobuf(spanData, outputStream);
      }
      else if (format == GradleTelemetryFormat.JSON) {
        serializeIntoJson(spanData, outputStream);
      }
      return outputStream.toByteArray();
    }
    catch (Exception e) {
      return ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }
  }

  /**
   * There is no normal way to explicitly use TraceRequestMarshaler. Gradle Tooling API collects the class path while serializing class by
   * parsing a binary class representation (org.gradle.tooling.internal.provider.serialization.ClasspathInferer).
   * <p>
   * 1) a part of IDEA-specific JARs can leak into a Gradle daemon
   * 2) ClasspathInferer strictly rely on current IDEA classloader. For example, classloaders with support for JEP-238 (Multiversion Jar)
   * will break serialization, because TraceRequestMarshaler uses Jackson deeply inside and Jackson is packaged into Multiversion Jar.
   * As a result, ClasspathInferer can load an older version of ASM ClassReader and at the same time, parse Jackson components compiled
   * into Java 17+ bytecode.
   */
  private static void serializeIntoProtobuf(@NotNull Collection<SpanData> spanData, @NotNull OutputStream outputStream)
    throws ReflectiveOperationException {
    Object marshaller = Class.forName("io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler")
      .getMethod("create", Collection.class)
      .invoke(null, spanData);
    marshaller.getClass()
      .getMethod("writeBinaryTo", OutputStream.class)
      .invoke(marshaller, outputStream);
  }

  private static void serializeIntoJson(@NotNull Collection<SpanData> spanData, @NotNull OutputStream outputStream)
    throws ReflectiveOperationException {
    Object marshaller = Class.forName("io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler")
      .getMethod("create", Collection.class)
      .invoke(null, spanData);
    marshaller.getClass()
      .getMethod("writeJsonTo", OutputStream.class)
      .invoke(marshaller, outputStream);
  }
}
