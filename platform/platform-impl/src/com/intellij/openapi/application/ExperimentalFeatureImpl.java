/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.application;

/**
 * @author Konstantin Bulenkov
 */
public final class ExperimentalFeatureImpl extends ExperimentalFeature{
  public boolean isEnabled() {
    Application app = ApplicationManager.getApplication();
    if (app == null) return false;
    if (!app.isEAP()) return false;
    if (internalFeature && !app.isInternal()) return false;

    if (percentOfUsers <= 0) return false;
    if (percentOfUsers >= 100) return true;
    if (app.isUnitTestMode()) return false;

    int hash = (PermanentInstallationID.get() + id).hashCode();
    return Math.floorMod(hash, 100) <= percentOfUsers;
  }
}
