/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.util.io.FileUtil;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
@Deprecated
/**
 * Use {@link com.intellij.util.text.UniqueNameGenerator}
 */
public class UniqueFileNamesProvider {
  private final List<String> myExistingNames;

  public UniqueFileNamesProvider() {
    myExistingNames = new ArrayList<String>();
  }

  public String suggestName(String originalName) {
    String s = FileUtil.sanitizeFileName(originalName, false);
    if (!contains(s)) {
      myExistingNames.add(s);
      return s;
    }

    for (int postfix = myExistingNames.size(); ; postfix++){
      String s1 = s + postfix;
      if (!contains(s1)) {
        myExistingNames.add(s1);
        return s1;
      }
    }
  }

  private boolean contains(String s) {
    for (int i = 0; i < myExistingNames.size(); i++) {
      if (myExistingNames.get(i).equalsIgnoreCase(s)) {
        return true;
      }
    }
    return false;
  }

  public static String convertName(String s) {
    return FileUtil.sanitizeFileName(s, false);
  }

  public void reserveFileName(final String fileName) {
    myExistingNames.add(fileName);
  }
}
