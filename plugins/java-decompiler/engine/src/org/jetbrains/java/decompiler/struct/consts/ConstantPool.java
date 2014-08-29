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
package org.jetbrains.java.decompiler.struct.consts;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.renamer.PoolInterceptor;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConstantPool {

  public static final int FIELD = 1;

  public static final int METHOD = 2;

  // *****************************************************************************
  // private fields
  // *****************************************************************************

  private List<PooledConstant> pool = new ArrayList<PooledConstant>();

  private PoolInterceptor interceptor;

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  public ConstantPool(DataInputStream in) throws IOException {

    int size = in.readUnsignedShort();

    int[] pass = new int[size];

    // first dummy constant
    pool.add(null);

    // first pass: read the elements
    for (int i = 1; i < size; i++) {

      byte tag = (byte)in.readUnsignedByte();

      switch (tag) {
        case CodeConstants.CONSTANT_Utf8:
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Utf8, in.readUTF()));
          break;
        case CodeConstants.CONSTANT_Integer:
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Integer, new Integer(in.readInt())));
          break;
        case CodeConstants.CONSTANT_Float:
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Float, new Float(in.readFloat())));
          break;
        case CodeConstants.CONSTANT_Long:
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Long, new Long(in.readLong())));
          pool.add(null);
          i++;
          break;
        case CodeConstants.CONSTANT_Double:
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Double, new Double(in.readDouble())));
          pool.add(null);
          i++;
          break;
        case CodeConstants.CONSTANT_Class:
        case CodeConstants.CONSTANT_String:
        case CodeConstants.CONSTANT_MethodType:
          pool.add(new PrimitiveConstant(tag, in.readUnsignedShort()));
          pass[i] = 1;
          break;
        case CodeConstants.CONSTANT_Fieldref:
        case CodeConstants.CONSTANT_Methodref:
        case CodeConstants.CONSTANT_InterfaceMethodref:
        case CodeConstants.CONSTANT_NameAndType:
        case CodeConstants.CONSTANT_InvokeDynamic:
          pool.add(new LinkConstant(tag, in.readUnsignedShort(), in.readUnsignedShort()));
          if (tag == CodeConstants.CONSTANT_NameAndType) {
            pass[i] = 1;
          }
          else {
            pass[i] = 2;
          }
          break;
        case CodeConstants.CONSTANT_MethodHandle:
          pool.add(new LinkConstant(tag, in.readUnsignedByte(), in.readUnsignedShort()));
          pass[i] = 3;
          break;
      }
    }


    // resolving complex pool elements
    for (int pass_index = 1; pass_index <= 3; pass_index++) {
      for (int i = 1; i < size; i++) {
        if (pass[i] == pass_index) {
          pool.get(i).resolveConstant(this);
        }
      }
    }

    // get global constant pool interceptor instance, if any available
    interceptor = DecompilerContext.getPoolInterceptor();
  }

  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public void writeToOutputStream(DataOutputStream out) throws IOException {

    out.writeShort(pool.size());
    for (int i = 1; i < pool.size(); i++) {
      PooledConstant cnst = pool.get(i);
      if (cnst != null) {
        cnst.writeToStream(out);
      }
    }
  }

  public static void skipPool(DataInputStream in) throws IOException {

    int size = in.readUnsignedShort();

    for (int i = 1; i < size; i++) {
      switch (in.readUnsignedByte()) {
        case CodeConstants.CONSTANT_Utf8:
          in.readUTF();
          break;
        case CodeConstants.CONSTANT_Integer:
        case CodeConstants.CONSTANT_Float:
        case CodeConstants.CONSTANT_Fieldref:
        case CodeConstants.CONSTANT_Methodref:
        case CodeConstants.CONSTANT_InterfaceMethodref:
        case CodeConstants.CONSTANT_NameAndType:
        case CodeConstants.CONSTANT_InvokeDynamic:
          in.skip(4);
          break;
        case CodeConstants.CONSTANT_Long:
        case CodeConstants.CONSTANT_Double:
          in.skip(8);
          i++;
          break;
        case CodeConstants.CONSTANT_Class:
        case CodeConstants.CONSTANT_String:
        case CodeConstants.CONSTANT_MethodType:
          in.skip(2);
          break;
        case CodeConstants.CONSTANT_MethodHandle:
          in.skip(3);
      }
    }
  }

  public int size() {
    return pool.size();
  }

  public String[] getClassElement(int element_type, int class_index, int name_index, int descriptor_index) {

    String classname = ((PrimitiveConstant)getConstant(class_index)).getString();
    String elementname = ((PrimitiveConstant)getConstant(name_index)).getString();
    String descriptor = ((PrimitiveConstant)getConstant(descriptor_index)).getString();

    if (interceptor != null) {
      String new_element = interceptor.getName(classname + " " + elementname + " " + descriptor);

      if (new_element != null) {
        elementname = new_element.split(" ")[1];
      }

      String new_descriptor = buildNewDescriptor(element_type == FIELD ? CodeConstants.CONSTANT_Fieldref : CodeConstants.CONSTANT_Methodref,
                                                 descriptor);
      if (new_descriptor != null) {
        descriptor = new_descriptor;
      }
    }

    return new String[]{elementname, descriptor};
  }

  public PooledConstant getConstant(int index) {
    return pool.get(index);
  }

  public PrimitiveConstant getPrimitiveConstant(int index) {
    PrimitiveConstant cn = (PrimitiveConstant)getConstant(index);

    if (cn != null && interceptor != null) {
      if (cn.type == CodeConstants.CONSTANT_Class) {
        String newname = buildNewClassname(cn.getString());
        if (newname != null) {
          cn = new PrimitiveConstant(CodeConstants.CONSTANT_Class, newname);
        }
      }
    }

    return cn;
  }

  public LinkConstant getLinkConstant(int index) {
    LinkConstant ln = (LinkConstant)getConstant(index);

    if (ln != null && interceptor != null) {
      if (ln.type == CodeConstants.CONSTANT_Fieldref ||
          ln.type == CodeConstants.CONSTANT_Methodref ||
          ln.type == CodeConstants.CONSTANT_InterfaceMethodref) {

        String new_classname = buildNewClassname(ln.classname);
        String new_element = interceptor.getName(ln.classname + " " + ln.elementname + " " + ln.descriptor);
        String new_descriptor = buildNewDescriptor(ln.type, ln.descriptor);

        if (new_classname != null || new_element != null || new_descriptor != null) {

          ln = new LinkConstant(ln.type, new_classname == null ? ln.classname : new_classname,
                                new_element == null ? ln.elementname : new_element.split(" ")[1],
                                new_descriptor == null ? ln.descriptor : new_descriptor);
        }
      }
    }

    return ln;
  }

  private String buildNewClassname(String classname) {

    VarType vt = new VarType(classname, true);

    String newname = interceptor.getName(vt.value);
    if (newname != null) {
      StringBuilder buffer = new StringBuilder();

      if (vt.arraydim > 0) {
        for (int i = 0; i < vt.arraydim; i++) {
          buffer.append("[");
        }

        buffer.append("L" + newname + ";");
      }
      else {
        buffer.append(newname);
      }

      return buffer.toString();
    }

    return null;
  }

  private String buildNewDescriptor(int type, String descriptor) {

    boolean updated = false;

    if (type == CodeConstants.CONSTANT_Fieldref) {
      FieldDescriptor fd = FieldDescriptor.parseDescriptor(descriptor);

      VarType ftype = fd.type;
      if (ftype.type == CodeConstants.TYPE_OBJECT) {
        String newclname = buildNewClassname(ftype.value);
        if (newclname != null) {
          ftype.value = newclname;
          updated = true;
        }
      }

      if (updated) {
        return fd.getDescriptor();
      }
    }
    else {

      MethodDescriptor md = MethodDescriptor.parseDescriptor(descriptor);
      // params
      for (VarType partype : md.params) {
        if (partype.type == CodeConstants.TYPE_OBJECT) {
          String newclname = buildNewClassname(partype.value);
          if (newclname != null) {
            partype.value = newclname;
            updated = true;
          }
        }
      }

      // return value
      if (md.ret.type == CodeConstants.TYPE_OBJECT) {
        String newclname = buildNewClassname(md.ret.value);
        if (newclname != null) {
          md.ret.value = newclname;
          updated = true;
        }
      }

      if (updated) {
        return md.getDescriptor();
      }
    }

    return null;
  }
}
