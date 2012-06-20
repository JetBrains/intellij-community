/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.android.util;

import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
public class ResourceEntry {
  private final String myType;
  private final String myName;
  private final String myContext;

  public ResourceEntry(@NotNull String type, @NotNull String name, @NotNull String context) {
    myType = type;
    myName = name;
    myContext = context;
  }

  @NotNull
  public String getContext() {
    return myContext;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ResourceEntry that = (ResourceEntry)o;

    if (!myContext.equals(that.myContext)) return false;
    if (!myName.equals(that.myName)) return false;
    if (!myType.equals(that.myType)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myType.hashCode();
    result = 31 * result + myName.hashCode();
    result = 31 * result + myContext.hashCode();
    return result;
  }
}
