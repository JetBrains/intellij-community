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
package com.intellij.util.text;

import java.util.StringTokenizer;

/**
 * @author mike
 */
public class CloneableTokenizer extends StringTokenizer implements Cloneable {
  public CloneableTokenizer(String str) {
    super(str);
  }

  public CloneableTokenizer(String str, String delim) {
    super(str, delim);
  }

  public CloneableTokenizer(String str, String delim, boolean returnDelims) {
    super(str, delim, returnDelims);
  }

  public Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      e.printStackTrace();
    }

    return null;
  }
}
