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
package com.intellij.openapi.vcs.changes.patch;

import java.io.File;

public class PatchNameChecker {
  public final static int MAX = 100;
  private final static int MAX_PATH = 255; // Windows path len restrictions
  private final String myName;
  private boolean myPreventsOk;
  private String myError;
  private final String myPath;

  public PatchNameChecker(final String name) {
    myPath = name;
    myName = new File(name).getName();
    myPreventsOk = false;
  }

  public boolean nameOk() {
    if (myName == null || myName.length() == 0) {
      myError = "File name cannot be empty";
      myPreventsOk = true;
      return false;
    } else if (myPath.length() > MAX_PATH) {
      myError = "File path should not be too long.";
      myPreventsOk = true;
      return false;
    } else if (new File(myPath).exists()) {
      myError = "File with the same name already exists";
      myPreventsOk = false;
      return false;
    }
    return true;
  }

  public boolean isPreventsOk() {
    return myPreventsOk;
  }

  public String getError() {
    return myError;
  }
}
