// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.diagnostic.Logger;

/**
 * @author Konstantin Bulenkov
 */
public final class ExperimentalFeatureImpl extends ExperimentalFeature{
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
