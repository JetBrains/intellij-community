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

package com.intellij.lang.ant.typedefs;

import org.apache.tools.ant.Task;

public class AntCustomTask extends Task {

  private String myString;
  private int myInteger;
  private boolean myBoolean;

  public void setString(final String string) {
    myString = string;
  }

  public String getString() {
    return myString;
  }

  public void setInteger(final int integer) {
    myInteger = integer;
  }

  public int getInteger() {
    return myInteger;
  }

  public void setBoolean(final boolean aBoolean) {
    myBoolean = aBoolean;
  }

  public boolean getBoolean() {
    return myBoolean;
  }
}
