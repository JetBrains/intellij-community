/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.igtest.cloneable.clone_calls_super_clone;

public class CloneCallsSuperClone implements Cloneable
{
    
    public void foo()
    {
        
    }

    public Object <warning descr="Method 'clone()' does not call 'super.clone()'">clone</warning>()
    {
        return this;
    }
}
class One {

  public final One clone() throws CloneNotSupportedException {
    throw new CloneNotSupportedException();
  }
}
final class Two {
  public Two clone() throws CloneNotSupportedException {
    throw (new CloneNotSupportedException());
  }
}
class Three {
  public Three clone() throws CloneNotSupportedException {
    throw new CloneNotSupportedException();
  }
}
class Four {
  public final Four clone() {
    throw new UnsupportedOperationException();
  }
}
