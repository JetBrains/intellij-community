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
package com.intellij.openapi.diff.impl;



/**
 * @author Jeka
 */
public class DiffRange implements DiffFragmentBuilder.Range {
  private final int myStart;
  private final int myEnd;

  public DiffRange(int start, int end) {
    myStart = start;
    myEnd = end;
  }

  public int getStart() {
    return myStart;
  }

  public int getEnd() {
    return myEnd;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "DiffRange: " + myStart + "," + myEnd;
  }
}
