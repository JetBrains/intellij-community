// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkout;

import java.io.File;

/**
 * author: lesya
 */
public final class CreateDirectoryForFolderStrategy extends CheckoutStrategy{
  public CreateDirectoryForFolderStrategy(File selectedLocation, File cvsPath, boolean isForFile) {
    super(selectedLocation, cvsPath, isForFile);
  }

  @Override
  public File getResult() {
    if (isForFile() && (getSelectedLocation().getParentFile() == null)) return null;
    return new File(getSelectedLocation(), getCvsPath().getName());
  }

  @Override
  public boolean useAlternativeCheckoutLocation() {
    return true;
  }

  @Override
  public File getCheckoutDirectory() {
    if (isForFile()){
      return getSelectedLocation();
    } else {
      return new File(getSelectedLocation(), getCvsPath().getName());
    }
  }

}
