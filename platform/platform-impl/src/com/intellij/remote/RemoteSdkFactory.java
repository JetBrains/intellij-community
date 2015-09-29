/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remote;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;

/**
 * @author traff
 */
public interface RemoteSdkFactory<T extends RemoteSdkAdditionalData> {
  Sdk createRemoteSdk(@Nullable Project project, @NotNull T data, @Nullable String sdkName, Collection<Sdk> existingSdks)
    throws RemoteSdkException;

  String generateSdkHomePath(@NotNull T data);

  Sdk createUnfinished(T data, Collection<Sdk> existingSdks);

  String getDefaultUnfinishedName();

  @NotNull
  String sdkName();

  boolean canSaveUnfinished();

  void initSdk(@NotNull Sdk sdk, @Nullable Project project, @Nullable Component ownerComponent);
}
