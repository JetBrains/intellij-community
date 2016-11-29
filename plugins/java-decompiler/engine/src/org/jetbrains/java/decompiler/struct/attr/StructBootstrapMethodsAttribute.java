/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.java.decompiler.struct.consts.PooledConstant;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StructBootstrapMethodsAttribute extends StructGeneralAttribute {

  private final List<LinkConstant> methodRefs = new ArrayList<>();
  private final List<List<PooledConstant>> methodArguments = new ArrayList<>();

  @Override
  public void initContent(ConstantPool pool) throws IOException {
    DataInputStream data = stream();

    int method_number = data.readUnsignedShort();

    for (int i = 0; i < method_number; ++i) {
      int bootstrap_method_ref = data.readUnsignedShort();
      int num_bootstrap_arguments = data.readUnsignedShort();

      List<PooledConstant> list_arguments = new ArrayList<>();

      for (int j = 0; j < num_bootstrap_arguments; ++j) {
        int bootstrap_argument_ref = data.readUnsignedShort();

        list_arguments.add(pool.getConstant(bootstrap_argument_ref));
      }

      methodRefs.add(pool.getLinkConstant(bootstrap_method_ref));
      methodArguments.add(list_arguments);
    }
  }

  public int getMethodsNumber() {
    return methodRefs.size();
  }

  public LinkConstant getMethodReference(int index) {
    return methodRefs.get(index);
  }

  public List<PooledConstant> getMethodArguments(int index) {
    return methodArguments.get(index);
  }
}
