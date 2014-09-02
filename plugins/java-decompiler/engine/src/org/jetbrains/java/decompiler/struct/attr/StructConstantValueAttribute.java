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
package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;

public class StructConstantValueAttribute extends StructGeneralAttribute {

  private int index;

  public void initContent(ConstantPool pool) {

    name = ATTRIBUTE_CONSTANT_VALUE;
    index = ((info[0] & 0xFF) << 8) | (info[1] & 0xFF);
  }

  public int getIndex() {
    return index;
  }
}
