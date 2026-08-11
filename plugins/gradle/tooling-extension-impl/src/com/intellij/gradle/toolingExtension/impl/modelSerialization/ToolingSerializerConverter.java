// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelSerialization;

import com.intellij.gradle.toolingExtension.impl.telemetry.GradleOpenTelemetry;
import com.intellij.util.ExceptionUtilRt;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.Exceptions;

@ApiStatus.Internal
public class ToolingSerializerConverter {

  private final ToolingSerializer mySerializer;

  public ToolingSerializerConverter(@NotNull ClassLoader modelBuildersClassLoader) {
    mySerializer = new ToolingSerializer(modelBuildersClassLoader);
  }

  public Object convert(Object object) {
    return GradleOpenTelemetry.callWithSpan("SerializeGradleModel", span -> {
      span.setAttribute("model.class", object.getClass().getName());
      try {
        return mySerializer.write(object);
      }
      catch (SerializationServiceNotFoundException ignore) {
      }
      catch (Exception e) {
        Throwable unwrap = Exceptions.unwrap(e);
        if (object instanceof IdeaProject) {
          ExceptionUtilRt.rethrowUnchecked(unwrap);
          throw new RuntimeException(unwrap);
        }
        //noinspection UseOfSystemOutOrSystemErr
        System.err.println(ExceptionUtilRt.getThrowableText(unwrap, "org.jetbrains."));
      }
      return object;
    });
  }
}
