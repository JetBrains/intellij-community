// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct;

import org.jetbrains.java.decompiler.code.*;
import org.jetbrains.java.decompiler.struct.attr.StructCodeAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTableAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.Type;
import org.jetbrains.java.decompiler.util.DataInputFullStream;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jetbrains.java.decompiler.code.CodeConstants.*;

/*
  method_info {
    u2 access_flags;
    u2 name_index;
    u2 descriptor_index;
    u2 attributes_count;
    attribute_info attributes[attributes_count];
  }
*/
public class StructMethod extends StructMember {
  public static StructMethod create(DataInputFullStream in, ConstantPool pool, String clQualifiedName, int bytecodeVersion, boolean own) throws IOException {
    int accessFlags = in.readUnsignedShort();
    int nameIndex = in.readUnsignedShort();
    int descriptorIndex = in.readUnsignedShort();

    String[] values = pool.getClassElement(ConstantPool.METHOD, clQualifiedName, nameIndex, descriptorIndex);

    Map<String, StructGeneralAttribute> attributes = readAttributes(in, pool);
    StructCodeAttribute code = (StructCodeAttribute)attributes.remove(StructGeneralAttribute.ATTRIBUTE_CODE.name);
    if (code != null) {
      attributes.putAll(code.codeAttributes);
    }

    return new StructMethod(accessFlags, attributes, values[0], values[1], bytecodeVersion, own ? code : null);
  }

  private static final int[] opr_iconst = {-1, 0, 1, 2, 3, 4, 5};
  private static final int[] opr_loadstore = {0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3};
  private static final int[] opcs_load = {opc_iload, opc_lload, opc_fload, opc_dload, opc_aload};
  private static final int[] opcs_store = {opc_istore, opc_lstore, opc_fstore, opc_dstore, opc_astore};

  private final String name;
  private final String descriptor;
  private final int bytecodeVersion;
  private final int localVariables;
  private final int codeLength;
  private final int codeFullLength;
  private InstructionSequence seq = null;
  private boolean expanded = false;

  private StructMethod(int accessFlags,
                       Map<String, StructGeneralAttribute> attributes,
                       String name,
                       String descriptor,
                       int bytecodeVersion,
                       StructCodeAttribute code) {
    super(accessFlags, attributes);
    this.name = name;
    this.descriptor = descriptor;
    this.bytecodeVersion = bytecodeVersion;
    if (code != null) {
      this.localVariables = code.localVariables;
      this.codeLength = code.codeLength;
      this.codeFullLength = code.codeFullLength;
    }
    else {
      this.localVariables = this.codeLength = this.codeFullLength = -1;
    }
  }

  public void expandData(StructClass classStruct) throws IOException {
    if (codeLength >= 0 && !expanded) {
      byte[] code = classStruct.getLoader().loadBytecode(classStruct, this, codeFullLength);
      seq = parseBytecode(new DataInputFullStream(code), codeLength, classStruct.getPool());
      expanded = true;
    }
  }

  public void releaseResources() {
    if (codeLength >= 0 && expanded) {
      seq = null;
      expanded = false;
    }
  }

  @SuppressWarnings("AssignmentToForLoopParameter")
  private InstructionSequence parseBytecode(DataInputFullStream in, int length, ConstantPool pool) throws IOException {
    VBStyleCollection<Instruction, Integer> instructions = new VBStyleCollection<>();

    for (int i = 0; i < length; ) {
      int offset = i;

      int opcode = in.readUnsignedByte();
      int group = GROUP_GENERAL;

      boolean wide = (opcode == opc_wide);

      if (wide) {
        i++;
        opcode = in.readUnsignedByte();
      }

      List<Integer> operands = new ArrayList<>();

      if (opcode >= opc_iconst_m1 && opcode <= opc_iconst_5) {
        operands.add(opr_iconst[opcode - opc_iconst_m1]);
        opcode = opc_bipush;
      }
      else if (opcode >= opc_iload_0 && opcode <= opc_aload_3) {
        operands.add(opr_loadstore[opcode - opc_iload_0]);
        opcode = opcs_load[(opcode - opc_iload_0) / 4];
      }
      else if (opcode >= opc_istore_0 && opcode <= opc_astore_3) {
        operands.add(opr_loadstore[opcode - opc_istore_0]);
        opcode = opcs_store[(opcode - opc_istore_0) / 4];
      }
      else {
        switch (opcode) {
          case opc_bipush -> {
            operands.add((int)in.readByte());
            i++;
          }
          case opc_ldc, opc_newarray -> {
            operands.add(in.readUnsignedByte());
            i++;
          }
          case opc_sipush, opc_ifeq, opc_ifne, opc_iflt, opc_ifge, opc_ifgt, opc_ifle, opc_if_icmpeq, opc_if_icmpne, opc_if_icmplt,
            opc_if_icmpge, opc_if_icmpgt, opc_if_icmple, opc_if_acmpeq, opc_if_acmpne, opc_goto, opc_jsr, opc_ifnull, opc_ifnonnull -> {
            if (opcode != opc_sipush) {
              group = GROUP_JUMP;
            }
            operands.add((int)in.readShort());
            i += 2;
          }
          case opc_ldc_w, opc_ldc2_w, opc_getstatic, opc_putstatic, opc_getfield, opc_putfield, opc_invokevirtual, opc_invokespecial,
            opc_invokestatic, opc_new, opc_anewarray, opc_checkcast, opc_instanceof -> {
            operands.add(in.readUnsignedShort());
            i += 2;
            if (opcode >= opc_getstatic && opcode <= opc_putfield) {
              group = GROUP_FIELDACCESS;
            }
            else if (opcode >= opc_invokevirtual && opcode <= opc_invokestatic) {
              group = GROUP_INVOCATION;
            }
          }
          case opc_invokedynamic -> {
            if (bytecodeVersion >= CodeConstants.BYTECODE_JAVA_7) { // instruction unused in Java 6 and before
              operands.add(in.readUnsignedShort());
              in.discard(2);
              group = GROUP_INVOCATION;
              i += 4;
            }
          }
          case opc_iload, opc_lload, opc_fload, opc_dload, opc_aload, opc_istore, opc_lstore,
            opc_fstore, opc_dstore, opc_astore, opc_ret -> {
            if (wide) {
              operands.add(in.readUnsignedShort());
              i += 2;
            }
            else {
              operands.add(in.readUnsignedByte());
              i++;
            }
            if (opcode == opc_ret) {
              group = GROUP_RETURN;
            }
          }
          case opc_iinc -> {
            if (wide) {
              operands.add(in.readUnsignedShort());
              operands.add((int)in.readShort());
              i += 4;
            }
            else {
              operands.add(in.readUnsignedByte());
              operands.add((int)in.readByte());
              i += 2;
            }
          }
          case opc_goto_w, opc_jsr_w -> {
            opcode = opcode == opc_jsr_w ? opc_jsr : opc_goto;
            operands.add(in.readInt());
            group = GROUP_JUMP;
            i += 4;
          }
          case opc_invokeinterface -> {
            operands.add(in.readUnsignedShort());
            operands.add(in.readUnsignedByte());
            in.discard(1);
            group = GROUP_INVOCATION;
            i += 4;
          }
          case opc_multianewarray -> {
            operands.add(in.readUnsignedShort());
            operands.add(in.readUnsignedByte());
            i += 3;
          }
          case opc_tableswitch -> {
            in.discard((4 - (i + 1) % 4) % 4);
            i += ((4 - (i + 1) % 4) % 4); // padding
            operands.add(in.readInt());
            i += 4;
            int low = in.readInt();
            operands.add(low);
            i += 4;
            int high = in.readInt();
            operands.add(high);
            i += 4;

            for (int j = 0; j < high - low + 1; j++) {
              operands.add(in.readInt());
              i += 4;
            }
            group = GROUP_SWITCH;
          }
          case opc_lookupswitch -> {
            in.discard((4 - (i + 1) % 4) % 4);
            i += ((4 - (i + 1) % 4) % 4); // padding
            operands.add(in.readInt());
            i += 4;
            int npairs = in.readInt();
            operands.add(npairs);
            i += 4;

            for (int j = 0; j < npairs; j++) {
              operands.add(in.readInt());
              i += 4;
              operands.add(in.readInt());
              i += 4;
            }
            group = GROUP_SWITCH;
          }
          case opc_ireturn, opc_lreturn, opc_freturn, opc_dreturn, opc_areturn, opc_return, opc_athrow ->
            group = GROUP_RETURN;
        }
      }

      int[] ops = null;
      if (!operands.isEmpty()) {
        ops = new int[operands.size()];
        for (int j = 0; j < operands.size(); j++) {
          ops[j] = operands.get(j);
        }
      }

      Instruction instr = Instruction.create(opcode, wide, group, bytecodeVersion, ops);

      instructions.addWithKey(instr, offset);

      i++;
    }

    // initialize exception table
    List<ExceptionHandler> lstHandlers = new ArrayList<>();

    int exception_count = in.readUnsignedShort();
    for (int i = 0; i < exception_count; i++) {
      ExceptionHandler handler = new ExceptionHandler();
      handler.from = in.readUnsignedShort();
      handler.to = in.readUnsignedShort();
      handler.handler = in.readUnsignedShort();

      int excclass = in.readUnsignedShort();
      if (excclass != 0) {
        handler.exceptionClass = pool.getPrimitiveConstant(excclass).getString();
      }

      lstHandlers.add(handler);
    }

    InstructionSequence seq = new FullInstructionSequence(instructions, new ExceptionTable(lstHandlers));

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

  public String getName() {
    return name;
  }

  public String getDescriptor() {
    return descriptor;
  }

  public int getBytecodeVersion() {
    return bytecodeVersion;
  }

  public boolean containsCode() {
    return codeLength >= 0;
  }

  public int getLocalVariables() {
    return localVariables;
  }

  public InstructionSequence getInstructionSequence() {
    return seq;
  }

  public StructLocalVariableTableAttribute getLocalVariableAttr() {
    return getAttribute(StructGeneralAttribute.ATTRIBUTE_LOCAL_VARIABLE_TABLE);
  }

  @Override
  protected Type getType() {
    return MethodDescriptor.parseDescriptor(getDescriptor()).ret;
  }

  @Override
  public String toString() {
    return name;
  }
}
