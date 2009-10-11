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

public class SingleEnumeration implements Enumeration{
  private final Object myValue;
  private final boolean myAdvanced;

  public SingleEnumeration(Object value){
    myValue = value;
    myAdvanced = false;
  }

  public Object nextElement(){
    if (myAdvanced){
      throw new NoSuchElementException();
    }
    return myValue;
  }

  public boolean hasMoreElements(){
    return !myAdvanced;
  }
}
