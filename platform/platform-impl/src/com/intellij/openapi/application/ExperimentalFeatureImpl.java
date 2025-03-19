// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;

/**
 * Please use the registry to enable/disable experimental features, see {@code com.intellij.registryKey} extension point
 *
 * @author Konstantin Bulenkov
 */
public final class ExperimentalFeatureImpl extends ExperimentalFeature{
  @ApiStatus.Internal
  public ExperimentalFeatureImpl() {
  }

  @ApiStatus.Internal
  @Override
  public boolean isEnabled() {
    Application app = ApplicationManager.getApplication();
    if (app == null) return false;
    if (internalFeature && !app.isInternal()) return false;

    if (percentOfUsers <= 0) return false;
    if (percentOfUsers >= 100) return true;
    if (!app.isEAP()) {
      Logger.getInstance(getClass()).warn("Feature '" + id + "' is disabled in Release. Set 'percentOfUsers' to 100% to enable in Release.");
      return false;
    }
    if (app.isUnitTestMode()) return false;

    int hash = (PermanentInstallationID.get() + id).hashCode();
    return Math.floorMod(hash, 100) <= percentOfUsers;
  }
}
