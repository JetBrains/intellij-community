/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.appengine.sdk.impl;

import com.intellij.appengine.facet.AppEngineWebIntegration;
import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.sdk.AppEngineSdkManager;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class AppEngineSdkManagerImpl extends AppEngineSdkManager {
  private final Map<String, AppEngineSdkImpl> myPath2Sdk = new THashMap<>();

  @NotNull
  @Override
  public AppEngineSdk findSdk(@NotNull String sdkPath) {
    sdkPath = StringUtil.trimEnd(sdkPath, "/");
    if (!myPath2Sdk.containsKey(sdkPath)) {
      myPath2Sdk.put(sdkPath, new AppEngineSdkImpl(sdkPath));
    }
    return myPath2Sdk.get(sdkPath);
  }

  @NotNull
  @Override
  public List<? extends AppEngineSdk> getValidSdks() {
    return AppEngineWebIntegration.getInstance().getSdkForConfiguredDevServers();
  }
}
