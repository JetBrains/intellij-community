/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import java.util.Arrays;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.02.2008
 */
public class MyPair  {
  public String first = null;
  public String second = null;

  public MyPair() {
  }

  public MyPair(String first, String second) {
    this.first = first;
    this.second = second;
  }

  public void setFirst(String first) {
    this.first = first;
  }
  
  public void setSecond(String second) {
    this.second = second;
  }

  public final int hashCode() {
    int hashCode = 0;
    if (first != null) {
      hashCode += hashCode(first);
    }
    if (second != null) {
      hashCode += hashCode(second);
    }
    return hashCode;
  }

  private static int hashCode(final Object o) {
    return (o instanceof Object[]) ? Arrays.hashCode((Object[]) o) : o.hashCode();
  }

  public String toString() {
    return "<" + first + "," + second + ">";
  }
}
