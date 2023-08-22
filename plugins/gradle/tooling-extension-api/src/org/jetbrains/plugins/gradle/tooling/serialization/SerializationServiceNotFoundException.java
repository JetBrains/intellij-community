// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization;

public class SerializationServiceNotFoundException extends Exception {
  public <T> SerializationServiceNotFoundException(Class<T> modelClazz) {
    super("Can not find serialization service for " + modelClazz.getName());
  }
}
