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
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;

public class StructEnclosingMethodAttribute extends StructGeneralAttribute {

  private String classname;

  private String mtname;

  private String methodDescriptor;

  public void initContent(ConstantPool pool) {

    name = ATTRIBUTE_ENCLOSING_METHOD;

    int clindex = (((info[0] & 0xFF) << 8) | (info[1] & 0xFF));
    int mtindex = (((info[2] & 0xFF) << 8) | (info[3] & 0xFF));

    classname = pool.getPrimitiveConstant(clindex).getString();
    if (mtindex != 0) {
      LinkConstant lk = pool.getLinkConstant(mtindex);

      mtname = lk.elementname;
      methodDescriptor = lk.descriptor;
    }
  }

  public String getClassname() {
    return classname;
  }

  public String getMethodDescriptor() {
    return methodDescriptor;
  }

  public String getMethodName() {
    return mtname;
  }
}
