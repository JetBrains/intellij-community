// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.util.ExceptionUtilRt;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.internal.DummyModel;
import org.jetbrains.plugins.gradle.tooling.Exceptions;
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationServiceNotFoundException;
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingSerializer;

@ApiStatus.Internal
public final class ProjectImportActionWithCustomSerializer extends ProjectImportAction {
  public ProjectImportActionWithCustomSerializer(boolean isPreviewMode, boolean isCompositeBuildsSupported) {
    super(isPreviewMode, isCompositeBuildsSupported);
  }

  @NotNull
  @Override
  protected ModelConverter getToolingModelConverter(@NotNull BuildController controller) {
    return new ToolingSerializerConverter(controller);
  }

  private static final class ToolingSerializerConverter implements ModelConverter {
    private final ToolingSerializer mySerializer;

    private ToolingSerializerConverter(@NotNull BuildController controller) {
      Object unpacked = new ProtocolToModelAdapter().unpack(controller.getModel(DummyModel.class));
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
}
