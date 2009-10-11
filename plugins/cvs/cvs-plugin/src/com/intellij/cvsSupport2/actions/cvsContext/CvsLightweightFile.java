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
package com.intellij.cvsSupport2.actions.cvsContext;

import java.io.File;

public class CvsLightweightFile {
  private final File myCvsFile;
  private final File myLocalFile;

  public CvsLightweightFile(File cvsFile, File localFile) {
    myCvsFile = cvsFile;
    myLocalFile = localFile;
  }

  public File getCvsFile() {
    return myCvsFile;
  }

  public File getLocalFile() {
    return myLocalFile;
  }

  public File getRoot() {
    if (myLocalFile == null) return null;
    return getRoot(myLocalFile);
  }

  private static File getRoot(File current) {
    while (current != null){
      if (current.isDirectory()) return current;
      current = current.getParentFile();
    }
    return null;
  }

  public String getModuleName() {
    return myCvsFile.getPath().replace(File.separatorChar, '/');
  }

}