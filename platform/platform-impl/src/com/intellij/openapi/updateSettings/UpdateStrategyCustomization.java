/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings;

import com.intellij.openapi.components.ServiceManager;

/**
 * Override this service in your IDE to customize update behavior. It isn't supposed to be overridden in plugins.
 */
public class UpdateStrategyCustomization {
  public static UpdateStrategyCustomization getInstance() {
    return ServiceManager.getService(UpdateStrategyCustomization.class);
  }

  public boolean forceEapUpdateChannelForEapBuilds() {
    return true;
  }

  /**
   * Whether the updater will allow patch updates to cross major version boundaries.
   */
  public boolean allowMajorVersionUpdate() {
    return false;
  }
}
