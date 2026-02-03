// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkout;

import java.io.File;

/**
 * author: lesya
 */
public final class CheckoutFolderToTheSameFolder extends CheckoutStrategy {
  public CheckoutFolderToTheSameFolder(File selectedLocation, File cvsPath, boolean isForFile) {
    super(selectedLocation, cvsPath, isForFile);
  }

  @Override
  public File getResult() {
    if (isForFile()) return null;
    if (getSelectedLocation().getParentFile() == null) return null;
    return getSelectedLocation();
  }

  @Override
  public boolean useAlternativeCheckoutLocation() {
    return true;
  }

  @Override
  public File getCheckoutDirectory() {
    return getSelectedLocation();
  }

}
