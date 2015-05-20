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
package org.jetbrains.java.decompiler.struct.lazy;

import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LazyLoader {

  private final Map<String, Link> mapClassLinks = new HashMap<String, Link>();
  private final IBytecodeProvider provider;

  public LazyLoader(IBytecodeProvider provider) {
    this.provider = provider;
  }

  public void addClassLink(String classname, Link link) {
    mapClassLinks.put(classname, link);
  }

  public void removeClassLink(String classname) {
    mapClassLinks.remove(classname);
  }

  public Link getClassLink(String classname) {
    return mapClassLinks.get(classname);
  }

  public ConstantPool loadPool(String classname) {
    try {
      DataInputFullStream in = getClassStream(classname);
      if (in == null) return null;

      try {
        in.discard(8);
        return new ConstantPool(in);
      }
      finally {
        in.close();
      }
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public byte[] loadBytecode(StructMethod mt, int codeFullLength) {
    String className = mt.getClassStruct().qualifiedName;

    try {
      DataInputFullStream in = getClassStream(className);
      if (in == null) return null;

      try {
        in.discard(8);

        ConstantPool pool = mt.getClassStruct().getPool();
        if (pool == null) {
          pool = new ConstantPool(in);
        }
        else {
          ConstantPool.skipPool(in);
        }

        in.discard(6);

        // interfaces
        in.discard(in.readUnsignedShort() * 2);

        // fields
        int size = in.readUnsignedShort();
        for (int i = 0; i < size; i++) {
          in.discard(6);
          skipAttributes(in);
        }

        // methods
        size = in.readUnsignedShort();
        for (int i = 0; i < size; i++) {
          in.discard(2);

          int nameIndex = in.readUnsignedShort();
          int descriptorIndex = in.readUnsignedShort();

          String[] values = pool.getClassElement(ConstantPool.METHOD, className, nameIndex, descriptorIndex);
          if (!mt.getName().equals(values[0]) || !mt.getDescriptor().equals(values[1])) {
            skipAttributes(in);
            continue;
          }

          int attrSize = in.readUnsignedShort();
          for (int j = 0; j < attrSize; j++) {
            int attrNameIndex = in.readUnsignedShort();
            String attrName = pool.getPrimitiveConstant(attrNameIndex).getString();
            if (!StructGeneralAttribute.ATTRIBUTE_CODE.equals(attrName)) {
              in.discard(in.readInt());
              continue;
            }

            in.discard(12);
            byte[] code = new byte[codeFullLength];
            in.readFull(code);
            return code;
          }

          break;
        }
      }
      finally {
        in.close();
      }

      return null;
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public DataInputFullStream getClassStream(String externalPath, String internalPath) throws IOException {
    byte[] bytes = provider.getBytecode(externalPath, internalPath);
    return new DataInputFullStream(bytes);
  }

  public DataInputFullStream getClassStream(String qualifiedClassName) throws IOException {
    Link link = mapClassLinks.get(qualifiedClassName);
    return link == null ? null : getClassStream(link.externalPath, link.internalPath);
  }

  public static void skipAttributes(DataInputFullStream in) throws IOException {
    int length = in.readUnsignedShort();
    for (int i = 0; i < length; i++) {
      in.discard(2);
      in.discard(in.readInt());
    }
  }


  public static class Link {
    public static final int CLASS = 1;
    public static final int ENTRY = 2;

    public final int type;
    public final String externalPath;
    public final String internalPath;

    public Link(int type, String externalPath, String internalPath) {
      this.type = type;
      this.externalPath = externalPath;
      this.internalPath = internalPath;
    }
  }
}
