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
package org.jetbrains.java.decompiler.struct;

import org.jetbrains.java.decompiler.code.*;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTableAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
    method_info {
    	u2 access_flags;
    	u2 name_index;
    	u2 descriptor_index;
    	u2 attributes_count;
    	attribute_info attributes[attributes_count];
    }
*/

public class StructMethod implements CodeConstants {

  // *****************************************************************************
  // public fields
  // *****************************************************************************

  public int name_index;

  public int descriptor_index;

  // *****************************************************************************
  // private fields
  // *****************************************************************************

  private static final int[] opr_iconst = new int[]{-1, 0, 1, 2, 3, 4, 5};

  private static final int[] opr_loadstore = new int[]{0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3};

  private static final int[] opcs_load = new int[]{opc_iload, opc_lload, opc_fload, opc_dload, opc_aload};

  private static final int[] opcs_store = new int[]{opc_istore, opc_lstore, opc_fstore, opc_dstore, opc_astore};


  private int accessFlags;

  private VBStyleCollection<StructGeneralAttribute, String> attributes;

  private int localVariables;

  private int maxStack;

  private String name;

  private String descriptor;

  private InstructionSequence seq;

  private boolean containsCode = false;

  private boolean own;

  private StructClass classStruct;


  // lazy properties
  private boolean lazy;

  private boolean expanded;

  private byte[] code_content;

  private int code_length = 0;

  private int code_fulllength = 0;

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  public StructMethod(DataInputFullStream in, boolean own, StructClass clstruct) throws IOException {
    this(in, true, own, clstruct);
  }

  public StructMethod(DataInputFullStream in, boolean lazy, boolean own, StructClass clstruct) throws IOException {

    this.own = own;
    this.lazy = lazy;
    this.expanded = !lazy;
    this.classStruct = clstruct;

    accessFlags = in.readUnsignedShort();
    name_index = in.readUnsignedShort();
    descriptor_index = in.readUnsignedShort();

    ConstantPool pool = clstruct.getPool();

    initStrings(pool, clstruct.this_class);

    VBStyleCollection<StructGeneralAttribute, String> lstAttribute = new VBStyleCollection<StructGeneralAttribute, String>();
    int len = in.readUnsignedShort();
    for (int i = 0; i < len; i++) {

      int attr_nameindex = in.readUnsignedShort();
      String attrname = pool.getPrimitiveConstant(attr_nameindex).getString();

      if (StructGeneralAttribute.ATTRIBUTE_CODE.equals(attrname)) {
        if (!this.own) {
          // skip code in foreign classes
          in.skip(8);
          in.skip(in.readInt());
          in.skip(8 * in.readUnsignedShort());
        }
        else {
          containsCode = true;

          in.skip(4);

          maxStack = in.readUnsignedShort();
          localVariables = in.readUnsignedShort();

          if (lazy) {
            code_length = in.readInt();

            in.skip(code_length);

            int exc_length = in.readUnsignedShort();
            code_fulllength = code_length + exc_length * 8 + 2;

            in.skip(exc_length * 8);
          }
          else {
            seq = parseBytecode(in, in.readInt(), pool);
          }
        }

        // code attributes
        int length = in.readUnsignedShort();
        for (int j = 0; j < length; j++) {
          int codeattr_nameindex = in.readUnsignedShort();
          String codeattrname = pool.getPrimitiveConstant(codeattr_nameindex).getString();

          readAttribute(in, pool, lstAttribute, codeattr_nameindex, codeattrname);
        }
      }
      else {
        readAttribute(in, pool, lstAttribute, attr_nameindex, attrname);
      }
    }

    attributes = lstAttribute;
  }


  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public void writeToStream(DataOutputStream out) throws IOException {

    out.writeShort(accessFlags);
    out.writeShort(name_index);
    out.writeShort(descriptor_index);

    out.writeShort(attributes.size());

    for (StructGeneralAttribute attr : attributes) {
      if (StructGeneralAttribute.ATTRIBUTE_CODE.equals(attr.getName())) {
        out.writeShort(attr.getAttribute_name_index());

        if (lazy && !expanded) {
          out.writeInt(10 + code_content.length);
          out.writeShort(maxStack);
          out.writeShort(localVariables);
          out.writeInt(code_length);
          out.write(code_content);
        }
        else {
          ByteArrayOutputStream codeout = new ByteArrayOutputStream();
          seq.writeCodeToStream(new DataOutputStream(codeout));

          ByteArrayOutputStream excout = new ByteArrayOutputStream();
          seq.writeExceptionsToStream(new DataOutputStream(excout));

          out.writeInt(10 + codeout.size() + excout.size());

          out.writeShort(maxStack);
          out.writeShort(localVariables);
          out.writeInt(codeout.size());
          codeout.writeTo(out);
          excout.writeTo(out);
        }
        // no attributes
        out.writeShort(0);
      }
      else {
        attr.writeToStream(out);
      }
    }
  }

  private void readAttribute(DataInputFullStream in, ConstantPool pool, VBStyleCollection<StructGeneralAttribute, String> lstAttribute,
                             int attr_nameindex, String attrname) throws IOException {

    StructGeneralAttribute attribute = StructGeneralAttribute.getMatchingAttributeInstance(attr_nameindex, attrname);

    if (attribute != null) {
      attrname = attribute.getName();

      byte[] arr = new byte[in.readInt()];
      in.readFull(arr);
      attribute.setInfo(arr);

      attribute.initContent(pool);

      if (StructGeneralAttribute.ATTRIBUTE_LOCAL_VARIABLE_TABLE.equals(attrname) &&
          lstAttribute.containsKey(attrname)) {
        // merge all variable tables
        StructLocalVariableTableAttribute oldattr = (StructLocalVariableTableAttribute)lstAttribute.getWithKey(attrname);
        oldattr.addLocalVariableTable((StructLocalVariableTableAttribute)attribute);
      }
      else {
        lstAttribute.addWithKey(attribute, attribute.getName());
      }
    }
    else {
      in.skip(in.readInt());
    }
  }

  private void initStrings(ConstantPool pool, int class_index) {
    String[] values = pool.getClassElement(ConstantPool.METHOD, class_index, name_index, descriptor_index);
    name = values[0];
    descriptor = values[1];
  }

  public void expandData() throws IOException {
    if (containsCode && lazy && !expanded) {

      byte[] codearr = classStruct.getLoader().loadBytecode(this, code_fulllength);

      seq = parseBytecode(new DataInputFullStream(new ByteArrayInputStream(codearr)), code_length, classStruct.getPool());
      expanded = true;
    }
  }

  public void releaseResources() throws IOException {
    if (containsCode && lazy && expanded) {
      seq = null;
      expanded = false;
    }
  }

  // *****************************************************************************
  // private methods
  // *****************************************************************************

  private InstructionSequence parseBytecode(DataInputFullStream in, int length, ConstantPool pool) throws IOException {

    VBStyleCollection<Instruction, Integer> collinstr = new VBStyleCollection<Instruction, Integer>();

    int bytecode_version = classStruct.getBytecodeVersion();

    for (int i = 0; i < length; ) {

      int offset = i;

      int opcode = in.readUnsignedByte();
      int group = GROUP_GENERAL;

      boolean wide = (opcode == opc_wide);

      if (wide) {
        i++;
        opcode = in.readUnsignedByte();
      }

      List<Integer> operands = new ArrayList<Integer>();

      if (opcode >= opc_iconst_m1 && opcode <= opc_iconst_5) {
        operands.add(new Integer(opr_iconst[opcode - opc_iconst_m1]));
        opcode = opc_bipush;
      }
      else if (opcode >= opc_iload_0 && opcode <= opc_aload_3) {
        operands.add(new Integer(opr_loadstore[opcode - opc_iload_0]));
        opcode = opcs_load[(opcode - opc_iload_0) / 4];
      }
      else if (opcode >= opc_istore_0 && opcode <= opc_astore_3) {
        operands.add(new Integer(opr_loadstore[opcode - opc_istore_0]));
        opcode = opcs_store[(opcode - opc_istore_0) / 4];
      }
      else {
        switch (opcode) {
          case opc_bipush:
            operands.add(new Integer(in.readByte()));
            i++;
            break;
          case opc_ldc:
          case opc_newarray:
            operands.add(new Integer(in.readUnsignedByte()));
            i++;
            break;
          case opc_sipush:
          case opc_ifeq:
          case opc_ifne:
          case opc_iflt:
          case opc_ifge:
          case opc_ifgt:
          case opc_ifle:
          case opc_if_icmpeq:
          case opc_if_icmpne:
          case opc_if_icmplt:
          case opc_if_icmpge:
          case opc_if_icmpgt:
          case opc_if_icmple:
          case opc_if_acmpeq:
          case opc_if_acmpne:
          case opc_goto:
          case opc_jsr:
          case opc_ifnull:
          case opc_ifnonnull:
            if (opcode != opc_sipush) {
              group = GROUP_JUMP;
            }
            operands.add(new Integer(in.readShort()));
            i += 2;
            break;
          case opc_ldc_w:
          case opc_ldc2_w:
          case opc_getstatic:
          case opc_putstatic:
          case opc_getfield:
          case opc_putfield:
          case opc_invokevirtual:
          case opc_invokespecial:
          case opc_invokestatic:
          case opc_new:
          case opc_anewarray:
          case opc_checkcast:
          case opc_instanceof:
            operands.add(new Integer(in.readUnsignedShort()));
            i += 2;
            if (opcode >= opc_getstatic && opcode <= opc_putfield) {
              group = GROUP_FIELDACCESS;
            }
            else if (opcode >= opc_invokevirtual && opcode <= opc_invokestatic) {
              group = GROUP_INVOCATION;
            }
            break;
          case opc_invokedynamic:
            if (classStruct.isVersionGE_1_7()) { // instruction unused in Java 6 and before
              operands.add(new Integer(in.readUnsignedShort()));
              in.skip(2);
              group = GROUP_INVOCATION;
              i += 4;
            }
            break;
          case opc_iload:
          case opc_lload:
          case opc_fload:
          case opc_dload:
          case opc_aload:
          case opc_istore:
          case opc_lstore:
          case opc_fstore:
          case opc_dstore:
          case opc_astore:
          case opc_ret:
            if (wide) {
              operands.add(new Integer(in.readUnsignedShort()));
              i += 2;
            }
            else {
              operands.add(new Integer(in.readUnsignedByte()));
              i++;
            }
            if (opcode == opc_ret) {
              group = GROUP_RETURN;
            }
            break;
          case opc_iinc:
            if (wide) {
              operands.add(new Integer(in.readUnsignedShort()));
              operands.add(new Integer(in.readShort()));
              i += 4;
            }
            else {
              operands.add(new Integer(in.readUnsignedByte()));
              operands.add(new Integer(in.readByte()));
              i += 2;
            }
            break;
          case opc_goto_w:
          case opc_jsr_w:
            opcode = opcode == opc_jsr_w ? opc_jsr : opc_goto;
            operands.add(new Integer(in.readInt()));
            group = GROUP_JUMP;
            i += 4;
            break;
          case opc_invokeinterface:
            operands.add(new Integer(in.readUnsignedShort()));
            operands.add(new Integer(in.readUnsignedByte()));
            in.skip(1);
            group = GROUP_INVOCATION;
            i += 4;
            break;
          case opc_multianewarray:
            operands.add(new Integer(in.readUnsignedShort()));
            operands.add(new Integer(in.readUnsignedByte()));
            i += 3;
            break;
          case opc_tableswitch:
            in.skip((4 - (i + 1) % 4) % 4);
            i += ((4 - (i + 1) % 4) % 4); // padding
            operands.add(new Integer(in.readInt()));
            i += 4;
            int low = in.readInt();
            operands.add(new Integer(low));
            i += 4;
            int high = in.readInt();
            operands.add(new Integer(high));
            i += 4;

            for (int j = 0; j < high - low + 1; j++) {
              operands.add(new Integer(in.readInt()));
              i += 4;
            }
            group = GROUP_SWITCH;

            break;
          case opc_lookupswitch:
            in.skip((4 - (i + 1) % 4) % 4);
            i += ((4 - (i + 1) % 4) % 4); // padding
            operands.add(new Integer(in.readInt()));
            i += 4;
            int npairs = in.readInt();
            operands.add(new Integer(npairs));
            i += 4;

            for (int j = 0; j < npairs; j++) {
              operands.add(new Integer(in.readInt()));
              i += 4;
              operands.add(new Integer(in.readInt()));
              i += 4;
            }
            group = GROUP_SWITCH;
            break;
          case opc_ireturn:
          case opc_lreturn:
          case opc_freturn:
          case opc_dreturn:
          case opc_areturn:
          case opc_return:
          case opc_athrow:
            group = GROUP_RETURN;
        }
      }

      int[] ops = new int[operands.size()];
      for (int j = 0; j < operands.size(); j++) {
        ops[j] = ((Integer)operands.get(j)).intValue();
      }

      Instruction instr = ConstantsUtil.getInstructionInstance(opcode, wide, group, bytecode_version, ops);

      collinstr.addWithKey(instr, new Integer(offset));

      i++;
    }

    // initialize exception table
    List<ExceptionHandler> lstHandlers = new ArrayList<ExceptionHandler>();

    int exception_count = in.readUnsignedShort();
    for (int i = 0; i < exception_count; i++) {
      ExceptionHandler handler = new ExceptionHandler();
      handler.from = in.readUnsignedShort();
      handler.to = in.readUnsignedShort();
      handler.handler = in.readUnsignedShort();

      int excclass = in.readUnsignedShort();
      handler.class_index = excclass;
      if (excclass != 0) {
        handler.exceptionClass = pool.getPrimitiveConstant(excclass).getString();
      }

      lstHandlers.add(handler);
    }

    InstructionSequence seq = new FullInstructionSequence(collinstr, new ExceptionTable(lstHandlers));

    // initialize instructions
    int i = seq.length() - 1;
    seq.setPointer(i);

    while (i >= 0) {
      Instruction instr = seq.getInstr(i--);
      if (instr.group != GROUP_GENERAL) {
        instr.initInstruction(seq);
      }
      seq.addToPointer(-1);
    }

    return seq;
  }

  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public InstructionSequence getInstructionSequence() {
    return seq;
  }

  public String getDescriptor() {
    return descriptor;
  }

  public String getName() {
    return name;
  }

  public int getAccessFlags() {
    return accessFlags;
  }

  public int getLocalVariables() {
    return localVariables;
  }

  public VBStyleCollection<StructGeneralAttribute, String> getAttributes() {
    return attributes;
  }

  public StructClass getClassStruct() {
    return classStruct;
  }

  public boolean containsCode() {
    return containsCode;
  }
}


