// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelSerialization;

import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;

@ApiStatus.Internal
public interface ModelConverter extends Serializable {

  Object convert(Object object);

  ModelConverter NOP = new ModelConverter() {
    @Override
    public Object convert(Object object) {
      return object;
    }
  };
}
