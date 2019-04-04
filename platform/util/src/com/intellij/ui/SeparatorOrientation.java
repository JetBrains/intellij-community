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
package com.intellij.ui;


public class SeparatorOrientation {
  public static final SeparatorOrientation HORIZONTAL = new SeparatorOrientation("HORIZONTAL");
  public static final SeparatorOrientation VERTICAL = new SeparatorOrientation("VERTICAL");

  private final String myName; // for debug only

  private SeparatorOrientation(String name) {
    myName = name;
  }

  public String toString() {
    return myName;
  }

}