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

public class DoubleEnumeration implements Enumeration{
  private final Object myValue1;
  private final Object myValue2;
  private int myIndex;

  public DoubleEnumeration(Object value1, Object value2){
    myValue1 = value1;
    myValue2 = value2;
    myIndex = 0;
  }

  public Object nextElement(){
    switch(myIndex++){
      case 0: return myValue1;
      case 1: return myValue2;
      default: throw new NoSuchElementException();
    }
  }

  public boolean hasMoreElements(){
    return myIndex < 2;
  }
}
