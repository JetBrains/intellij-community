// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelSerialization;

public class SerializationServiceNotFoundException extends Exception {
  public <T> SerializationServiceNotFoundException(Class<T> modelClazz) {
    super("Can not find serialization service for " + modelClazz.getName());
  }
}
