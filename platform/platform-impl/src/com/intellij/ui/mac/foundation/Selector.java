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
package com.intellij.ui.mac.foundation;

import com.sun.jna.NativeLong;

/**
 * @author spleaner
 */
public class Selector extends NativeLong {

  private String myName;

  public Selector() {
    this("undefined selector", 0);
  }

  public Selector(String name, long value) {
    super(value);
    myName = name;
  }

  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return String.format("[Selector %s]", myName);
  }

  public Selector initName(final String name) {
    myName = name;
    return this;
  }
}
