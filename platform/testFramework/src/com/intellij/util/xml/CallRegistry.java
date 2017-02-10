/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.xml;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class CallRegistry<T> {
  private int mySize;
  private final List<String> myExpected = new ArrayList<>();
  private final List<String> myActual = new ArrayList<>();

  public void putActual(T o) {
    myActual.add(o.toString());
    mySize++;
  }

  public void putExpected(T o) {
    myExpected.add(o.toString());
  }

  public void clear() {
    mySize = 0;
    myExpected.clear();
    myActual.clear();
  }

  public void assertResultsAndClear() {
    Assert.assertTrue(myActual.toString() + " " + myExpected.toString(), myActual.containsAll(myExpected));
    clear();
  }

  public String toString() {
    return myActual.toString();
  }

  public int getSize() {
    return mySize;
  }
}
