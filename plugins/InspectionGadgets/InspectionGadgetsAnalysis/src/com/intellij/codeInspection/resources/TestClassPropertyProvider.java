// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.resources;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.module.Module;

public interface TestClassPropertyProvider {
  ExtensionPointName<TestClassPropertyProvider>
    EP_NAME = ExtensionPointName.create("com.intellij.properties.files.provider");

  boolean hasTestClassProperty(@NotNull Module classModule);
}
