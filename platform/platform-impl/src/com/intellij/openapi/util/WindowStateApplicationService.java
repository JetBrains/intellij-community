// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Sergey.Malenkov
 */
@State(name = "WindowStateApplicationService", storages = @Storage(value = "window.state.xml", roamingType = RoamingType.DISABLED))
final class WindowStateApplicationService extends WindowStateServiceImpl {
  WindowStateApplicationService() {
    super(null);
  }

  @Override
  Point getDefaultLocationFor(@NotNull String key) {
    //  backward compatibility when this service is used instead of DimensionService
    return DimensionService.getInstance().getLocation(key);
  }

  @Override
  Dimension getDefaultSizeFor(@NotNull String key) {
    //  backward compatibility when this service is used instead of DimensionService
    return DimensionService.getInstance().getSize(key);
  }

  @Override
  Rectangle getDefaultBoundsFor(@NotNull String key) {
    Point location = getDefaultLocationFor(key);
    if (location == null) {
      return null;
    }
    Dimension size = getDefaultSizeFor(key);
    if (size == null) {
      return null;
    }
    return new Rectangle(location, size);
  }

  @Override
  boolean getDefaultMaximizedFor(Object object, @NotNull String key) {
    // todo move this data from DimensionService to this service
    return DimensionService.getInstance().getDefaultMaximizedFor(key);
  }
}
