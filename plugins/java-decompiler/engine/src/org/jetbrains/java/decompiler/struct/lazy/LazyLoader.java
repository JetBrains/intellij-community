// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  private final Map<String, Link> mapClassLinks = new HashMap<>();
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
    try (DataInputFullStream in = getClassStream(classname)) {
      if (in != null) {
        in.discard(8);
        return new ConstantPool(in);
      }

      return null;
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public byte[] loadBytecode(StructMethod mt, int codeFullLength) {
    String className = mt.getClassStruct().qualifiedName;

    try (DataInputFullStream in = getClassStream(className)) {
      if (in != null) {
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

            return in.read(codeFullLength);
          }

          break;
        }
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
    public final String externalPath;
    public final String internalPath;

    public Link(String externalPath, String internalPath) {
      this.externalPath = externalPath;
      this.internalPath = internalPath;
    }
  }
}