/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.checkout;

import java.io.File;

/**
 * author: lesya
 */
public abstract class CheckoutStrategy implements Comparable{
  private final File mySelectedLocation;
  private final File myCvsPath;
  private final boolean myIsForFile;

  public CheckoutStrategy(File selectedLocation, File cvsPath, boolean isForFile) {
    mySelectedLocation = selectedLocation;
    myCvsPath = cvsPath;
    myIsForFile = isForFile;
  }

  public static CheckoutStrategy[] createAllStrategies(File selectedLocation, File cvsPath, boolean isForFile){
    return new CheckoutStrategy[]{
      new SimpleCheckoutStrategy(selectedLocation, cvsPath, isForFile),
      new CheckoutFolderToTheSameFolder(selectedLocation, cvsPath, isForFile),
      new CreateDirectoryForFolderStrategy(selectedLocation, cvsPath, isForFile),
      new CheckoutToTheDirectoryWithModuleName(selectedLocation, cvsPath, isForFile)
    };
  }

  public int compareTo(Object o) {
    if (o instanceof CheckoutStrategy){
      return getResult().compareTo(((CheckoutStrategy)o).getResult());
    }
    return 0;
  }

  public File getSelectedLocation() {
    return mySelectedLocation;
  }

  public File getCvsPath() {
    return myCvsPath;
  }

  public boolean isForFile() {
    return myIsForFile;
  }

  public abstract File getResult();
  public abstract boolean useAlternativeCheckoutLocation();

  public abstract File getCheckoutDirectory();
}
