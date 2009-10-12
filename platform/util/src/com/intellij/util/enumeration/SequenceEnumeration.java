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
package com.intellij.util.enumeration;

import java.util.Enumeration;
import java.util.NoSuchElementException;

public class SequenceEnumeration implements Enumeration {
  private Enumeration myFirst;
  private Enumeration mySecond;
  private Enumeration myThird;
  private Enumeration myCurrent;
  private int myCurrentIndex;

  public SequenceEnumeration(Enumeration first, Enumeration second) {
    this(first, second, null);
  }

  public SequenceEnumeration(Enumeration first, Enumeration second, Enumeration third) {
    myFirst = first;
    mySecond = second;
    myThird = third;
    myCurrent = myFirst;
    myCurrentIndex = 0;
  }

  public boolean hasMoreElements() {
    if (myCurrentIndex == 3)
      return false;
    if (myCurrent != null && myCurrent.hasMoreElements()) {
      return true;
    }

    if (myCurrentIndex == 0) {
      myCurrent = mySecond;
    }
    else if (myCurrentIndex == 1) {
      myCurrent = myThird;
    }
    myCurrentIndex++;
    return hasMoreElements();
  }

  public Object nextElement() {
    if (!hasMoreElements()) {
      throw new NoSuchElementException();
    }
    return myCurrent.nextElement();
  }
}

