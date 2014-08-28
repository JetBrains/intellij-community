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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StructExceptionsAttribute extends StructGeneralAttribute {

  private List<Integer> throwsExceptions = new ArrayList<Integer>();

  public void initContent(ConstantPool pool) {

    name = ATTRIBUTE_EXCEPTIONS;

    int length = 2 + (((info[0] & 0xFF) << 8) | (info[1] & 0xFF)) * 2;
    for (int i = 2; i < length; i += 2) {
      int index = ((info[i] & 0xFF) << 8) | (info[i + 1] & 0xFF);
      throwsExceptions.add(index);
    }
  }

  public void writeToStream(DataOutputStream out) throws IOException {

    out.writeShort(attribute_name_index);

    ByteArrayOutputStream codeout = new ByteArrayOutputStream();
    DataOutputStream dataout = new DataOutputStream(codeout);

    int len = throwsExceptions.size();
    dataout.writeShort(len);

    if (len > 0) {
      info = new byte[len * 2];
      for (int i = 0, j = 0; i < len; i++, j += 2) {
        int index = ((Integer)throwsExceptions.get(i)).intValue();
        info[j] = (byte)(index >> 8);
        info[j + 1] = (byte)(index & 0xFF);
      }
      dataout.write(info);
    }

    out.writeInt(codeout.size());
    out.write(codeout.toByteArray());
  }

  public String getExcClassname(int index, ConstantPool pool) {
    return pool.getPrimitiveConstant(((Integer)throwsExceptions.get(index)).intValue()).getString();
  }

  public List<Integer> getThrowsExceptions() {
    return throwsExceptions;
  }

  public void setThrowsExceptions(List<Integer> throwsExceptions) {
    this.throwsExceptions = throwsExceptions;
  }
}
