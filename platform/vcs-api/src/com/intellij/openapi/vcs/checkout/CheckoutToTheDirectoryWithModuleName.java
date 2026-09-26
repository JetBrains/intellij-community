// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkout;

import java.io.File;

/**
 * author: lesya
 */
public final class CheckoutToTheDirectoryWithModuleName extends CheckoutStrategy{
  public CheckoutToTheDirectoryWithModuleName(File selectedLocation, File cvsPath, boolean isForFile) {
    super(selectedLocation, cvsPath, isForFile);
  }

  @Override
  public File getResult() {
    if (!getSelectedLocation().getName().equals(getTopLevelName(getCvsPath()))) return null;
    return new File(getSelectedLocation().getParentFile(), getCvsPath().getPath());
  }

  private static String getTopLevelName(File cvsPath) {
    File current = cvsPath;
    while(current.getParentFile() != null) current = current.getParentFile();
    return current.getName();
  }

  @Override
  public boolean useAlternativeCheckoutLocation() {
    return false;
  }

  @Override
  public File getCheckoutDirectory() {
    return getSelectedLocation().getParentFile();
  }
}
