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
package org.jetbrains.java.decompiler.code;

public interface CodeConstants {

  // ----------------------------------------------------------------------
  // BYTECODE VERSIONS
  // ----------------------------------------------------------------------

  public final static int BYTECODE_JAVA_LE_4 = 1;
  public final static int BYTECODE_JAVA_5 = 2;
  public final static int BYTECODE_JAVA_6 = 3;
  public final static int BYTECODE_JAVA_7 = 4;
  public final static int BYTECODE_JAVA_8 = 5;

  // ----------------------------------------------------------------------
  // VARIABLE TYPES
  // ----------------------------------------------------------------------

  public final static int TYPE_BYTE = 0;
  public final static int TYPE_CHAR = 1;
  public final static int TYPE_DOUBLE = 2;
  public final static int TYPE_FLOAT = 3;
  public final static int TYPE_INT = 4;
  public final static int TYPE_LONG = 5;
  public final static int TYPE_SHORT = 6;
  public final static int TYPE_BOOLEAN = 7;
  public final static int TYPE_OBJECT = 8;
  public final static int TYPE_ADDRESS = 9;
  public final static int TYPE_VOID = 10;
  public final static int TYPE_ANY = 11;
  public final static int TYPE_GROUP2EMPTY = 12;
  public final static int TYPE_NULL = 13;
  public final static int TYPE_NOTINITIALIZED = 14;
  public final static int TYPE_BYTECHAR = 15;
  public final static int TYPE_SHORTCHAR = 16;
  public final static int TYPE_UNKNOWN = 17;
  public final static int TYPE_GENVAR = 18;

  // ----------------------------------------------------------------------
  // VARIABLE TYPE FAMILIES
  // ----------------------------------------------------------------------

  public final static int TYPE_FAMILY_UNKNOWN = 0;
  public final static int TYPE_FAMILY_BOOLEAN = 1;
  public final static int TYPE_FAMILY_INTEGER = 2;
  public final static int TYPE_FAMILY_FLOAT = 3;
  public final static int TYPE_FAMILY_LONG = 4;
  public final static int TYPE_FAMILY_DOUBLE = 5;
  public final static int TYPE_FAMILY_OBJECT = 6;

  // ----------------------------------------------------------------------
  // MODULE CONSTANTS
  // ----------------------------------------------------------------------

  public final static int STACKSIZE_SIMPLE = 1;
  public final static int STACKSIZE_DOUBLE = 2;

  public final static int VAR_LOCAL = 0;
  public final static int VAR_STACK = 1;

  public final static int VAR_WRITE = 0;
  public final static int VAR_READ = 1;


  // ----------------------------------------------------------------------
  // ACCESS FLAGS
  // ----------------------------------------------------------------------

  public final static int ACC_PUBLIC = 0x0001;
  public final static int ACC_PRIVATE = 0x0002;
  public final static int ACC_PROTECTED = 0x0004;
  public final static int ACC_STATIC = 0x0008;
  public final static int ACC_FINAL = 0x0010;
  public final static int ACC_SYNCHRONIZED = 0x0020;
  public final static int ACC_NATIVE = 0x0100;
  public final static int ACC_ABSTRACT = 0x0400;
  public final static int ACC_STRICT = 0x0800;
  public final static int ACC_VOLATILE = 0x0040;
  public final static int ACC_BRIDGE = 0x0040;
  public final static int ACC_TRANSIENT = 0x0080;
  public final static int ACC_VARARGS = 0x0080;
  public final static int ACC_SYNTHETIC = 0x1000;
  public final static int ACC_ANNOTATION = 0x2000;
  public final static int ACC_ENUM = 0x4000;

  // ----------------------------------------------------------------------
  // CLASS FLAGS
  // ----------------------------------------------------------------------

  public final static int ACC_SUPER = 0x0020;
  public final static int ACC_INTERFACE = 0x0200;


  // ----------------------------------------------------------------------
  // DEPENDENCY CONSTANTS
  // ----------------------------------------------------------------------

  public final static int DEP_CONSTANT = 0;
  public final static int DEP_UNKNOWN = 1;
  public final static int DEP_GENERAL = 2;
  public final static int DEP_PARAMS = 4;
  public final static int DEP_STATIC = 8;

  // ----------------------------------------------------------------------
  // INSTRUCTION GROUPS
  // ----------------------------------------------------------------------

  public final static int GROUP_GENERAL = 1;
  public final static int GROUP_JUMP = 2;
  public final static int GROUP_SWITCH = 3;
  public final static int GROUP_INVOCATION = 4;
  public final static int GROUP_FIELDACCESS = 5;
  public final static int GROUP_RETURN = 6;

  // ----------------------------------------------------------------------
  // POOL CONSTANTS
  // ----------------------------------------------------------------------

  public final static int CONSTANT_Utf8 = 1;
  public final static int CONSTANT_Integer = 3;
  public final static int CONSTANT_Float = 4;
  public final static int CONSTANT_Long = 5;
  public final static int CONSTANT_Double = 6;
  public final static int CONSTANT_Class = 7;
  public final static int CONSTANT_String = 8;
  public final static int CONSTANT_Fieldref = 9;
  public final static int CONSTANT_Methodref = 10;
  public final static int CONSTANT_InterfaceMethodref = 11;
  public final static int CONSTANT_NameAndType = 12;
  public final static int CONSTANT_MethodHandle = 15;
  public final static int CONSTANT_MethodType = 16;
  public final static int CONSTANT_InvokeDynamic = 18;

  // ----------------------------------------------------------------------
  // MethodHandle reference_kind values
  // ----------------------------------------------------------------------

  public final static int CONSTANT_MethodHandle_REF_getField = 1;
  public final static int CONSTANT_MethodHandle_REF_getStatic = 2;
  public final static int CONSTANT_MethodHandle_REF_putField = 3;
  public final static int CONSTANT_MethodHandle_REF_putStatic = 4;
  public final static int CONSTANT_MethodHandle_REF_invokeVirtual = 5;
  public final static int CONSTANT_MethodHandle_REF_invokeStatic = 6;
  public final static int CONSTANT_MethodHandle_REF_invokeSpecial = 7;
  public final static int CONSTANT_MethodHandle_REF_newInvokeSpecial = 8;
  public final static int CONSTANT_MethodHandle_REF_invokeInterface = 9;

  // ----------------------------------------------------------------------
  // VM OPCODES
  // ----------------------------------------------------------------------

  public final static int opc_nop = 0;
  public final static int opc_aconst_null = 1;
  public final static int opc_iconst_m1 = 2;
  public final static int opc_iconst_0 = 3;
  public final static int opc_iconst_1 = 4;
  public final static int opc_iconst_2 = 5;
  public final static int opc_iconst_3 = 6;
  public final static int opc_iconst_4 = 7;
  public final static int opc_iconst_5 = 8;
  public final static int opc_lconst_0 = 9;
  public final static int opc_lconst_1 = 10;
  public final static int opc_fconst_0 = 11;
  public final static int opc_fconst_1 = 12;
  public final static int opc_fconst_2 = 13;
  public final static int opc_dconst_0 = 14;
  public final static int opc_dconst_1 = 15;
  public final static int opc_bipush = 16;
  public final static int opc_sipush = 17;
  public final static int opc_ldc = 18;
  public final static int opc_ldc_w = 19;
  public final static int opc_ldc2_w = 20;
  public final static int opc_iload = 21;
  public final static int opc_lload = 22;
  public final static int opc_fload = 23;
  public final static int opc_dload = 24;
  public final static int opc_aload = 25;
  public final static int opc_iload_0 = 26;
  public final static int opc_iload_1 = 27;
  public final static int opc_iload_2 = 28;
  public final static int opc_iload_3 = 29;
  public final static int opc_lload_0 = 30;
  public final static int opc_lload_1 = 31;
  public final static int opc_lload_2 = 32;
  public final static int opc_lload_3 = 33;
  public final static int opc_fload_0 = 34;
  public final static int opc_fload_1 = 35;
  public final static int opc_fload_2 = 36;
  public final static int opc_fload_3 = 37;
  public final static int opc_dload_0 = 38;
  public final static int opc_dload_1 = 39;
  public final static int opc_dload_2 = 40;
  public final static int opc_dload_3 = 41;
  public final static int opc_aload_0 = 42;
  public final static int opc_aload_1 = 43;
  public final static int opc_aload_2 = 44;
  public final static int opc_aload_3 = 45;
  public final static int opc_iaload = 46;
  public final static int opc_laload = 47;
  public final static int opc_faload = 48;
  public final static int opc_daload = 49;
  public final static int opc_aaload = 50;
  public final static int opc_baload = 51;
  public final static int opc_caload = 52;
  public final static int opc_saload = 53;
  public final static int opc_istore = 54;
  public final static int opc_lstore = 55;
  public final static int opc_fstore = 56;
  public final static int opc_dstore = 57;
  public final static int opc_astore = 58;
  public final static int opc_istore_0 = 59;
  public final static int opc_istore_1 = 60;
  public final static int opc_istore_2 = 61;
  public final static int opc_istore_3 = 62;
  public final static int opc_lstore_0 = 63;
  public final static int opc_lstore_1 = 64;
  public final static int opc_lstore_2 = 65;
  public final static int opc_lstore_3 = 66;
  public final static int opc_fstore_0 = 67;
  public final static int opc_fstore_1 = 68;
  public final static int opc_fstore_2 = 69;
  public final static int opc_fstore_3 = 70;
  public final static int opc_dstore_0 = 71;
  public final static int opc_dstore_1 = 72;
  public final static int opc_dstore_2 = 73;
  public final static int opc_dstore_3 = 74;
  public final static int opc_astore_0 = 75;
  public final static int opc_astore_1 = 76;
  public final static int opc_astore_2 = 77;
  public final static int opc_astore_3 = 78;
  public final static int opc_iastore = 79;
  public final static int opc_lastore = 80;
  public final static int opc_fastore = 81;
  public final static int opc_dastore = 82;
  public final static int opc_aastore = 83;
  public final static int opc_bastore = 84;
  public final static int opc_castore = 85;
  public final static int opc_sastore = 86;
  public final static int opc_pop = 87;
  public final static int opc_pop2 = 88;
  public final static int opc_dup = 89;
  public final static int opc_dup_x1 = 90;
  public final static int opc_dup_x2 = 91;
  public final static int opc_dup2 = 92;
  public final static int opc_dup2_x1 = 93;
  public final static int opc_dup2_x2 = 94;
  public final static int opc_swap = 95;
  public final static int opc_iadd = 96;
  public final static int opc_ladd = 97;
  public final static int opc_fadd = 98;
  public final static int opc_dadd = 99;
  public final static int opc_isub = 100;
  public final static int opc_lsub = 101;
  public final static int opc_fsub = 102;
  public final static int opc_dsub = 103;
  public final static int opc_imul = 104;
  public final static int opc_lmul = 105;
  public final static int opc_fmul = 106;
  public final static int opc_dmul = 107;
  public final static int opc_idiv = 108;
  public final static int opc_ldiv = 109;
  public final static int opc_fdiv = 110;
  public final static int opc_ddiv = 111;
  public final static int opc_irem = 112;
  public final static int opc_lrem = 113;
  public final static int opc_frem = 114;
  public final static int opc_drem = 115;
  public final static int opc_ineg = 116;
  public final static int opc_lneg = 117;
  public final static int opc_fneg = 118;
  public final static int opc_dneg = 119;
  public final static int opc_ishl = 120;
  public final static int opc_lshl = 121;
  public final static int opc_ishr = 122;
  public final static int opc_lshr = 123;
  public final static int opc_iushr = 124;
  public final static int opc_lushr = 125;
  public final static int opc_iand = 126;
  public final static int opc_land = 127;
  public final static int opc_ior = 128;
  public final static int opc_lor = 129;
  public final static int opc_ixor = 130;
  public final static int opc_lxor = 131;
  public final static int opc_iinc = 132;
  public final static int opc_i2l = 133;
  public final static int opc_i2f = 134;
  public final static int opc_i2d = 135;
  public final static int opc_l2i = 136;
  public final static int opc_l2f = 137;
  public final static int opc_l2d = 138;
  public final static int opc_f2i = 139;
  public final static int opc_f2l = 140;
  public final static int opc_f2d = 141;
  public final static int opc_d2i = 142;
  public final static int opc_d2l = 143;
  public final static int opc_d2f = 144;
  public final static int opc_i2b = 145;
  public final static int opc_i2c = 146;
  public final static int opc_i2s = 147;
  public final static int opc_lcmp = 148;
  public final static int opc_fcmpl = 149;
  public final static int opc_fcmpg = 150;
  public final static int opc_dcmpl = 151;
  public final static int opc_dcmpg = 152;
  public final static int opc_ifeq = 153;
  public final static int opc_ifne = 154;
  public final static int opc_iflt = 155;
  public final static int opc_ifge = 156;
  public final static int opc_ifgt = 157;
  public final static int opc_ifle = 158;
  public final static int opc_if_icmpeq = 159;
  public final static int opc_if_icmpne = 160;
  public final static int opc_if_icmplt = 161;
  public final static int opc_if_icmpge = 162;
  public final static int opc_if_icmpgt = 163;
  public final static int opc_if_icmple = 164;
  public final static int opc_if_acmpeq = 165;
  public final static int opc_if_acmpne = 166;
  public final static int opc_goto = 167;
  public final static int opc_jsr = 168;
  public final static int opc_ret = 169;
  public final static int opc_tableswitch = 170;
  public final static int opc_lookupswitch = 171;
  public final static int opc_ireturn = 172;
  public final static int opc_lreturn = 173;
  public final static int opc_freturn = 174;
  public final static int opc_dreturn = 175;
  public final static int opc_areturn = 176;
  public final static int opc_return = 177;
  public final static int opc_getstatic = 178;
  public final static int opc_putstatic = 179;
  public final static int opc_getfield = 180;
  public final static int opc_putfield = 181;
  public final static int opc_invokevirtual = 182;
  public final static int opc_invokespecial = 183;
  public final static int opc_invokestatic = 184;
  public final static int opc_invokeinterface = 185;
  public final static int opc_invokedynamic = 186;
  public final static int opc_xxxunusedxxx = 186;
  public final static int opc_new = 187;
  public final static int opc_newarray = 188;
  public final static int opc_anewarray = 189;
  public final static int opc_arraylength = 190;
  public final static int opc_athrow = 191;
  public final static int opc_checkcast = 192;
  public final static int opc_instanceof = 193;
  public final static int opc_monitorenter = 194;
  public final static int opc_monitorexit = 195;
  public final static int opc_wide = 196;
  public final static int opc_multianewarray = 197;
  public final static int opc_ifnull = 198;
  public final static int opc_ifnonnull = 199;
  public final static int opc_goto_w = 200;
  public final static int opc_jsr_w = 201;
}
