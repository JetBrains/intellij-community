// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization;

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.util.ClassMap;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ServiceLoader;

/**
 * @author Vladislav.Soroka
 */
public final class ToolingSerializer {
  private final DefaultSerializationService myDefaultSerializationService;
  private final ClassMap<SerializationService<?>> mySerializationServices;
  @Nullable private final ClassLoader myModelBuildersClassLoader;

  public ToolingSerializer() {
    this(null);
  }

  public ToolingSerializer(@Nullable ClassLoader modelBuildersClassLoader) {
    myModelBuildersClassLoader = modelBuildersClassLoader;
    myDefaultSerializationService = new DefaultSerializationService();
    mySerializationServices = new ClassMap<SerializationService<?>>();
    ClassLoader clientOwnedDaemonPayloadLoader = getClass().getClassLoader();
    if (modelBuildersClassLoader != null) {
      try {
        Class<?> serializationServiceClass = modelBuildersClassLoader.loadClass(SerializationService.class.getName());
        for (final Object serializationService : ServiceLoader.load(serializationServiceClass, modelBuildersClassLoader)) {
          SerializationService<?> proxyService = (SerializationService<?>)Proxy.newProxyInstance(
            SerializationService.class.getClassLoader(), new Class<?>[]{SerializationService.class},
            new InvocationHandler() {
              @Override
              public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                Method m = serializationService.getClass().getMethod(method.getName(), method.getParameterTypes());
                return m.invoke(serializationService, args);
              }
            });
          try {
            register(proxyService);
          } catch (Throwable ignore) {
            // ignore ClassNotFoundException/NoClassDefFoundError as builtin Gradle models presence might depend on specific Gradle versions
          }
        }

        addModelBuildersClassLoaderUrlsToTapiClientClassloader(modelBuildersClassLoader, clientOwnedDaemonPayloadLoader);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    for (SerializationService<?> serializerService : ServiceLoader.load(SerializationService.class, clientOwnedDaemonPayloadLoader)) {
      register(serializerService);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public byte[] write(@NotNull Object object) throws IOException, SerializationServiceNotFoundException {
    if (myModelBuildersClassLoader != null) {
      Object unpackedObject = maybeUnpack(object);
      Class unpackedObjectClass = unpackedObject.getClass();
      if (myModelBuildersClassLoader == unpackedObjectClass.getClassLoader()) {
        return getService(unpackedObjectClass, true).write(unpackedObject, unpackedObjectClass);
      }
    }
    Class modelClazz = object.getClass();
    return getService(modelClazz, false).write(object, modelClazz);
  }

  @Nullable
  public <T> T read(@NotNull byte[] object, @NotNull Class<T> modelClazz) throws IOException, SerializationServiceNotFoundException {
    assert myModelBuildersClassLoader == null;
    return getService(modelClazz, true).read(object, modelClazz);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @NotNull
  private <T> SerializationService<T> getService(@NotNull Class<T> modelClazz, boolean useDefaultSerializer)
    throws SerializationServiceNotFoundException {
    SerializationService service = mySerializationServices.get(modelClazz);
    if (service != null) return service;
    if (useDefaultSerializer) {
      return myDefaultSerializationService;
    }
    throw new SerializationServiceNotFoundException(modelClazz);
  }

  @NotNull
  private static Object maybeUnpack(@NotNull Object object) {
    try {
      return new ProtocolToModelAdapter().unpack(object);
    }
    catch (IllegalArgumentException ignore) {
    }
    return object;
  }

  private void register(@NotNull SerializationService<?> serializerService) {
    mySerializationServices.put(serializerService.getModelClass(), serializerService);
  }

  private static void addModelBuildersClassLoaderUrlsToTapiClientClassloader(@NotNull ClassLoader modelBuildersClassLoader,
                                                                             @NotNull ClassLoader clientOwnedDaemonPayloadLoader)
    throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Method addURLMethod = findMethodIncludingSuperclasses(clientOwnedDaemonPayloadLoader.getClass(), "addURL", URL.class);
    if (addURLMethod != null) {
      addURLMethod.setAccessible(true);
      URL[] modelBuildersClassLoaderUrls =
        (URL[])modelBuildersClassLoader.getClass().getMethod("getURLs").invoke(modelBuildersClassLoader);
      for (URL url : modelBuildersClassLoaderUrls) {
        addURLMethod.invoke(clientOwnedDaemonPayloadLoader, url);
      }
    }
  }

  @Nullable
  private static Method findMethodIncludingSuperclasses(Class<?> clazz, String name, Class<?>... parameterType) {
    try {
      return clazz.getDeclaredMethod(name, parameterType);
    }
    catch (NoSuchMethodException var5) {
      Class<?> superclass = clazz.getSuperclass();
      if (superclass != null) {
        return findMethodIncludingSuperclasses(superclass, name, parameterType);
      }
    }
    return null;
  }
}
