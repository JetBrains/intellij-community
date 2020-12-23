// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.sdk;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class AppEngineSdkManager {

  public static AppEngineSdkManager getInstance() {
    return ApplicationManager.getApplication().getService(AppEngineSdkManager.class);
  }

  @NotNull
  public abstract AppEngineSdk findSdk(@NotNull String sdkPath);

  @NotNull
  public abstract List<? extends AppEngineSdk> getValidSdks();

}
