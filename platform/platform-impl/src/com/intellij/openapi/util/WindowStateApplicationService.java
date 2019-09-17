// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

/**
 * @author Sergey.Malenkov
 */
@State(name = "WindowStateApplicationService", storages = @Storage(value = "window.state.xml", roamingType = RoamingType.DISABLED))
final class WindowStateApplicationService extends WindowStateServiceImpl {
  WindowStateApplicationService() {
    super(null);
  }
}
