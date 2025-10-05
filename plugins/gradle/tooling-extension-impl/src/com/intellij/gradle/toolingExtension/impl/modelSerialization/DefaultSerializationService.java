// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelSerialization;

import org.apache.commons.io.input.ClassLoaderObjectInputStream;
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationService;

import java.io.*;

/**
 * @author Vladislav.Soroka
 */
@SuppressWarnings("rawtypes")
public final class DefaultSerializationService implements SerializationService {
  @Override
  public byte[] write(Object object, Class modelClazz) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    try (ObjectOutput outputStream = new ObjectOutputStream(os)) {
      outputStream.writeObject(object);
    }
    catch (NotSerializableException e) {
      throw new IOException(String.format(
        "Implement Serializable or provide related org.jetbrains.plugins.gradle.tooling.serialization.SerializationService for the tooling model: '%s'",
        object.getClass().getName()), e);
    }
    return os.toByteArray();
  }

  @Override
  public Object read(byte[] object, final Class modelClazz) throws IOException {
    try (ObjectInput inputStream = new ClassLoaderObjectInputStream(modelClazz.getClassLoader(), new ByteArrayInputStream(object))) {
      return inputStream.readObject();
    }
    catch (ClassNotFoundException e) {
      throw new IOException(e);
    }
  }

  @Override
  public Class<Object> getModelClass() {
    throw new IllegalStateException("The method should never be called for this serializer service implementation");
  }
}
