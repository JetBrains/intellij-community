// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization;

import com.intellij.util.ExceptionUtilRt;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.internal.DummyModel;
import org.jetbrains.plugins.gradle.tooling.Exceptions;

@ApiStatus.Internal
public class ToolingSerializerConverter implements ModelConverter {
  private final ToolingSerializer mySerializer;

  public ToolingSerializerConverter(@NotNull BuildController controller) {
    DummyModel dummyModel = controller.getModel(DummyModel.class);
    Object unpacked = new ProtocolToModelAdapter().unpack(dummyModel);
    ClassLoader modelBuildersClassLoader = unpacked.getClass().getClassLoader();
    mySerializer = new ToolingSerializer(modelBuildersClassLoader);
  }

  @Override
  public Object convert(Object object) {
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
  }
}
