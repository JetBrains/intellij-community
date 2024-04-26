// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * The SdkBridge interface extends the Sdk interface and represents a bridge between different two
 * implementations of SDKs. The new one works via [WorkspaceModel] and use [SdkEntity] under the hood
 */
@ApiStatus.Internal
public interface SdkBridge extends Sdk {
  void changeType(@NotNull SdkTypeId newType, @Nullable Element additionalDataElement);
  void readExternal(@NotNull Element element);
  void readExternal(@NotNull Element element, @NotNull Function<String, SdkTypeId> sdkTypeByNameFunction) throws InvalidDataException;
  void writeExternal(@NotNull Element element);
  @NotNull SdkBridge clone();
}
