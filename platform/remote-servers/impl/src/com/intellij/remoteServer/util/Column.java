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
package com.intellij.remoteServer.util;

public abstract class Column<T> {

  private final String myName;

  public Column(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public Class<?> getValueClass() {
    return String.class;
  }

  public boolean isEditable() {
    return false;
  }

  public void setColumnValue(T row, Object value) {
    throw new UnsupportedOperationException();
  }

  public boolean needPack() {
    return false;
  }

  public abstract Object getColumnValue(T row);
}
