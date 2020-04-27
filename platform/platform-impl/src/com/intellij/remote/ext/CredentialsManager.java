// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ext;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.remote.CredentialsType;
import com.intellij.remote.OutdatedCredentialsType;
import com.intellij.remote.RemoteSdkAdditionalData;
import kotlin.Pair;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class CredentialsManager {

  public static CredentialsManager getInstance() {
    return ServiceManager.getService(CredentialsManager.class);
  }

  public abstract List<CredentialsType<?>> getAllTypes();

  public abstract void loadCredentials(String interpreterPath,
                                       @Nullable Element element,
                                       RemoteSdkAdditionalData data);

  public static void updateOutdatedSdk(@NotNull RemoteSdkAdditionalData<?> data, @Nullable Project project) {
    if (!(data.getRemoteConnectionType() instanceof OutdatedCredentialsType)) {
      return;
    }
    //noinspection unchecked
    Pair<CredentialsType<Object>, Object> pair = ((OutdatedCredentialsType)data.getRemoteConnectionType())
      .transformToNewerType(data.connectionCredentials().getCredentials(), project);
    data.setCredentials(pair.getFirst().getCredentialsKey(), pair.getSecond());
  }
}
