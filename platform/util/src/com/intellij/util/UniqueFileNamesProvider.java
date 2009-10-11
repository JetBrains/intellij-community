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
package com.intellij.util;

import java.util.ArrayList;

public class UniqueFileNamesProvider {
  private final ArrayList<String> myExistingNames;

  public UniqueFileNamesProvider() {
    myExistingNames = new ArrayList<String>();
  }

  public String suggestName(String originalName) {
    String s = convertName(originalName);
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
    if (s == null || s.length() == 0) {
      return "_";
    }
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isJavaIdentifierPart(c) || c == ' ') {
        buf.append(c);
      }
      else {
        buf.append('_');
      }
    }
    return buf.toString();
  }

  public void reserveFileName(final String fileName) {
    myExistingNames.add(fileName);
  }
}
