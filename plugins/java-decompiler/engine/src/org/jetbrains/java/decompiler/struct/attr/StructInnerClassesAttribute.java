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

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StructInnerClassesAttribute extends StructGeneralAttribute {
  public static class Entry {
    public final int innerNameIdx;
    public final int outerNameIdx;
    public final int simpleNameIdx;
    public final int accessFlags;
    public final String innerName;
    public final String enclosingName;
    public final String simpleName;

    private Entry(int innerNameIdx, int outerNameIdx, int simpleNameIdx, int accessFlags, String innerName, String enclosingName, String simpleName) {
      this.innerNameIdx = innerNameIdx;
      this.outerNameIdx = outerNameIdx;
      this.simpleNameIdx = simpleNameIdx;
      this.accessFlags = accessFlags;
      this.innerName = innerName;
      this.enclosingName = enclosingName;
      this.simpleName = simpleName;
    }
  }

  private List<Entry> entries;

  @Override
  public void initContent(ConstantPool pool) throws IOException {
    DataInputStream data = stream();

    int len = data.readUnsignedShort();
    if (len > 0) {
      entries = new ArrayList<>(len);

      for (int i = 0; i < len; i++) {
        int innerNameIdx = data.readUnsignedShort();
        int outerNameIdx = data.readUnsignedShort();
        int simpleNameIdx = data.readUnsignedShort();
        int accessFlags = data.readUnsignedShort();

        String innerName = pool.getPrimitiveConstant(innerNameIdx).getString();
        String outerName = outerNameIdx != 0 ? pool.getPrimitiveConstant(outerNameIdx).getString() : null;
        String simpleName = simpleNameIdx != 0 ? pool.getPrimitiveConstant(simpleNameIdx).getString() : null;

        entries.add(new Entry(innerNameIdx, outerNameIdx, simpleNameIdx, accessFlags, innerName, outerName, simpleName));
      }
    }
    else {
      entries = Collections.emptyList();
    }
  }

  public List<Entry> getEntries() {
    return entries;
  }
}
