/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.icons;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

public enum Theme {
  WHITE(null, "Default"),
  HIGH_DPI_WHITE("@2x", "Default HiDPI"), 
  DARK("dark", "Darcula"), 
  HIGH_DPI_DARK("@2x_dark", "Darcula HiDPI");
  private final String myExtension;
  private final String myDisplayName;

  Theme(String extension, String displayName) {
    myExtension = extension;
    myDisplayName = displayName;
  }

  public String getExtension() {
    return myExtension;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public boolean accepts(VirtualFile fileName) {
    String nameWithoutExtension = FileUtil.getNameWithoutExtension(fileName.getName());
    if (myExtension != null) {
      if (nameWithoutExtension.endsWith(myExtension)) {
        return true;
      }

      VirtualFile parent = fileName.getParent();
      if (parent != null && parent.findChild(nameWithoutExtension + myExtension + ".png") != null) {
        return false;
      }
    }

    for (Theme theme : values()) {
      String extension = theme.getExtension();
      if (extension != null && nameWithoutExtension.endsWith(extension)) {
        return false;
      }
    }

    return true;
  }


}
