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
package com.intellij.ui.growl;

import com.sun.jna.NativeLong;

/**
 * @author spleaner
 */
public class ID extends NativeLong {

  static ID fromLong(long value) {
    return new ID(value);
  }

  // for JNA
  public ID() {
    super();
  }

  protected ID(long value) {
    super(value);
  }

  protected ID(ID anotherID) {
    this(anotherID.longValue());
  }

  @Override
  public String toString() {
    return String.format("[ID 0x%x]", longValue()); //$NON-NLS-1$
  }

  public boolean isNull() {
    return longValue() == 0;
  }
}
