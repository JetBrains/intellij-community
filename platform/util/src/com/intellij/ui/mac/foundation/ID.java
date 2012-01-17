/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.mac.foundation;

import com.sun.jna.NativeLong;

/**
 * Could be an address in memory (if pointer to a class or method) or a value (like 0 or 1)
 *
 * User: spLeaner
 */
public class ID extends NativeLong {

  public ID() {
  }

  public ID(long peer) {
    super(peer);
  }
  
  public final static ID NIL = new ID(0);

}
