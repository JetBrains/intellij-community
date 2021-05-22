// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.util.ExceptionUtilRt;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.internal.DummyModel;
import org.jetbrains.plugins.gradle.tooling.Exceptions;
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationService;
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingSerializer;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.IdeaProjectSerializationService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

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
    private final Object mySerializer;
    private final Method mySerializerWriteMethod;
    private final ClassLoader myModelBuildersClassLoader;

    private ToolingSerializerConverter(@NotNull BuildController controller) {
      Object unpacked = new ProtocolToModelAdapter().unpack(controller.getModel(DummyModel.class));
      myModelBuildersClassLoader = unpacked.getClass().getClassLoader();
      try {
        Class<?> toolingSerializerClass = myModelBuildersClassLoader.loadClass(ToolingSerializer.class.getName());
        mySerializer = toolingSerializerClass.getDeclaredConstructor().newInstance();
        mySerializerWriteMethod = toolingSerializerClass.getMethod("write", Object.class, Class.class);
        registerGradleBuiltinModelsSerializationService(toolingSerializerClass);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public Object convert(Object object) {
      try {
        Object unpackedObject = unpackIfNeeded(object);
        if (unpackedObject != null) {
          return mySerializerWriteMethod.invoke(mySerializer, unpackedObject, unpackedObject.getClass());
        }
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

    // support custom serialization of the gradle built-in models
    private void registerGradleBuiltinModelsSerializationService(Class<?> toolingSerializerClass)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, ClassNotFoundException {
      Class<?> serializationServiceClass = myModelBuildersClassLoader.loadClass(SerializationService.class.getName());
      Method register = toolingSerializerClass.getMethod("register", serializationServiceClass);
      registerSerializationService(serializationServiceClass, register, new IdeaProjectSerializationService(getBuildGradleVersion()));
    }

    private void registerSerializationService(Class<?> serializationServiceClass, Method register,
                                              final SerializationService<?> serializationService)
      throws IllegalAccessException, InvocationTargetException {
      Object proxyInstance =
        Proxy.newProxyInstance(myModelBuildersClassLoader, new Class<?>[]{serializationServiceClass}, new InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Method m = serializationService.getClass().getMethod(method.getName(), method.getParameterTypes());
            return m.invoke(serializationService, args);
          }
        });
      register.invoke(mySerializer, proxyInstance);
    }

    @NotNull
    private GradleVersion getBuildGradleVersion() {
      try {
        Class<?> gradleVersionClass = myModelBuildersClassLoader.loadClass("org.gradle.util.GradleVersion");
        Object buildGradleVersion = gradleVersionClass.getMethod("current").invoke(gradleVersionClass);
        return GradleVersion.version(gradleVersionClass.getMethod("getVersion").invoke(buildGradleVersion).toString());
      }
      catch (Exception e) {
        ExceptionUtilRt.rethrowUnchecked(e);
        throw new RuntimeException(e);
      }
    }

    @Nullable
    private Object unpackIfNeeded(Object object) {
      if (!(object instanceof IdeaProject)) { // support custom serialization of the gradle built-in IdeaProject model
        try {
          object = new ProtocolToModelAdapter().unpack(object);
        }
        catch (IllegalArgumentException ignore) {
        }
        Class<?> modelClazz = object.getClass();
        if (modelClazz.getClassLoader() != myModelBuildersClassLoader) {
          //The object has not been created by custom model builders
          return null;
        }
      }
      return object;
    }
  }
}
