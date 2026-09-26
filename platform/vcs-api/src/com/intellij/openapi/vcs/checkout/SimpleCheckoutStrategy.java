// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkout;

import java.io.File;

/**
 * author: lesya
 */
public final class SimpleCheckoutStrategy extends CheckoutStrategy{
  public SimpleCheckoutStrategy(File selectedLocation, File cvsPath, boolean isForFile) {
    super(selectedLocation, cvsPath, isForFile);
  }

  @Override
  public boolean useAlternativeCheckoutLocation() {
    return false;
  }

  @Override
  public File getResult() {
    return new File(getSelectedLocation(), getCvsPath().getPath());
  }

  @Override
  public File getCheckoutDirectory() {
    return getSelectedLocation();
  }
}
