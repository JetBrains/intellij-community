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

public class LightEnumerationAdapter implements Enumeration{
  private final LightEnumeration myEnum;
  private Object myCurrent;

  public LightEnumerationAdapter(LightEnumeration enumeration){
    myEnum = enumeration;
    myCurrent = null;
  }

  public boolean hasMoreElements(){
    return getNextElement() != null;
  }

  public Object nextElement(){
    Object result = getNextElement();
    myCurrent = null;
    if (result != null){
      return result;
    }
    else{
      throw new NoSuchElementException();
    }
  }

  private Object getNextElement(){
    if (myCurrent != null) return myCurrent;
    myCurrent = myEnum.nextElement();
    return myCurrent;
  }
}

