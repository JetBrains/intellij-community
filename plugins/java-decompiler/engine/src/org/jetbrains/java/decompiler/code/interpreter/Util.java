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
package org.jetbrains.java.decompiler.code.interpreter;

import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructContext;


// FIXME: move to StructContext
public class Util {

  private static final String[][] runtime_exceptions = {

    null,                        //		public final static int		opc_nop = 0;
    null,                        //		public final static int		opc_aconst_null = 1;
    null,                        //		public final static int		opc_iconst_m1 = 2;
    null,                        //		public final static int		opc_iconst_0 = 3;
    null,                        //		public final static int		opc_iconst_1 = 4;
    null,                        //		public final static int		opc_iconst_2 = 5;
    null,                        //		public final static int		opc_iconst_3 = 6;
    null,                        //		public final static int		opc_iconst_4 = 7;
    null,                        //		public final static int		opc_iconst_5 = 8;
    null,                        //		public final static int		opc_lconst_0 = 9;
    null,                        //		public final static int		opc_lconst_1 = 10;
    null,                        //		public final static int		opc_fconst_0 = 11;
    null,                        //		public final static int		opc_fconst_1 = 12;
    null,                        //		public final static int		opc_fconst_2 = 13;
    null,                        //		public final static int		opc_dconst_0 = 14;
    null,                        //		public final static int		opc_dconst_1 = 15;
    null,                        //		public final static int		opc_bipush = 16;
    null,                        //		public final static int		opc_sipush = 17;
    null,                        //		public final static int		opc_ldc = 18;
    null,                        //		public final static int		opc_ldc_w = 19;
    null,                        //		public final static int		opc_ldc2_w = 20;
    null,                        //		public final static int		opc_iload = 21;
    null,                        //		public final static int		opc_lload = 22;
    null,                        //		public final static int		opc_fload = 23;
    null,                        //		public final static int		opc_dload = 24;
    null,                        //		public final static int		opc_aload = 25;
    null,                        //		public final static int		opc_iload_0 = 26;
    null,                        //		public final static int		opc_iload_1 = 27;
    null,                        //		public final static int		opc_iload_2 = 28;
    null,                        //		public final static int		opc_iload_3 = 29;
    null,                        //		public final static int		opc_lload_0 = 30;
    null,                        //		public final static int		opc_lload_1 = 31;
    null,                        //		public final static int		opc_lload_2 = 32;
    null,                        //		public final static int		opc_lload_3 = 33;
    null,                        //		public final static int		opc_fload_0 = 34;
    null,                        //		public final static int		opc_fload_1 = 35;
    null,                        //		public final static int		opc_fload_2 = 36;
    null,                        //		public final static int		opc_fload_3 = 37;
    null,                        //		public final static int		opc_dload_0 = 38;
    null,                        //		public final static int		opc_dload_1 = 39;
    null,                        //		public final static int		opc_dload_2 = 40;
    null,                        //		public final static int		opc_dload_3 = 41;
    null,                        //		public final static int		opc_aload_0 = 42;
    null,                        //		public final static int		opc_aload_1 = 43;
    null,                        //		public final static int		opc_aload_2 = 44;
    null,                        //		public final static int		opc_aload_3 = 45;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_iaload = 46;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_laload = 47;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_faload = 48;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_daload = 49;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_aaload = 50;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_baload = 51;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_caload = 52;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_saload = 53;
    null,                        //		public final static int		opc_istore = 54;
    null,                        //		public final static int		opc_lstore = 55;
    null,                        //		public final static int		opc_fstore = 56;
    null,                        //		public final static int		opc_dstore = 57;
    null,                        //		public final static int		opc_astore = 58;
    null,                        //		public final static int		opc_istore_0 = 59;
    null,                        //		public final static int		opc_istore_1 = 60;
    null,                        //		public final static int		opc_istore_2 = 61;
    null,                        //		public final static int		opc_istore_3 = 62;
    null,                        //		public final static int		opc_lstore_0 = 63;
    null,                        //		public final static int		opc_lstore_1 = 64;
    null,                        //		public final static int		opc_lstore_2 = 65;
    null,                        //		public final static int		opc_lstore_3 = 66;
    null,                        //		public final static int		opc_fstore_0 = 67;
    null,                        //		public final static int		opc_fstore_1 = 68;
    null,                        //		public final static int		opc_fstore_2 = 69;
    null,                        //		public final static int		opc_fstore_3 = 70;
    null,                        //		public final static int		opc_dstore_0 = 71;
    null,                        //		public final static int		opc_dstore_1 = 72;
    null,                        //		public final static int		opc_dstore_2 = 73;
    null,                        //		public final static int		opc_dstore_3 = 74;
    null,                        //		public final static int		opc_astore_0 = 75;
    null,                        //		public final static int		opc_astore_1 = 76;
    null,                        //		public final static int		opc_astore_2 = 77;
    null,                        //		public final static int		opc_astore_3 = 78;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_iastore = 79;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_lastore = 80;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_fastore = 81;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_dastore = 82;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException", "java/lang/ArrayStoreException"},
    //		public final static int		opc_aastore = 83;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_bastore = 84;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_castore = 85;
    {"java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"},
    //		public final static int		opc_sastore = 86;
    null,                        //		public final static int		opc_pop = 87;
    null,                        //		public final static int		opc_pop2 = 88;
    null,                        //		public final static int		opc_dup = 89;
    null,                        //		public final static int		opc_dup_x1 = 90;
    null,                        //		public final static int		opc_dup_x2 = 91;
    null,                        //		public final static int		opc_dup2 = 92;
    null,                        //		public final static int		opc_dup2_x1 = 93;
    null,                        //		public final static int		opc_dup2_x2 = 94;
    null,                        //		public final static int		opc_swap = 95;
    null,                        //		public final static int		opc_iadd = 96;
    null,                        //		public final static int		opc_ladd = 97;
    null,                        //		public final static int		opc_fadd = 98;
    null,                        //		public final static int		opc_dadd = 99;
    null,                        //		public final static int		opc_isub = 100;
    null,                        //		public final static int		opc_lsub = 101;
    null,                        //		public final static int		opc_fsub = 102;
    null,                        //		public final static int		opc_dsub = 103;
    null,                        //		public final static int		opc_imul = 104;
    null,                        //		public final static int		opc_lmul = 105;
    null,                        //		public final static int		opc_fmul = 106;
    null,                        //		public final static int		opc_dmul = 107;
    {"java/lang/ArithmeticException"},                        //		public final static int		opc_idiv = 108;
    {"java/lang/ArithmeticException"},                        //		public final static int		opc_ldiv = 109;
    null,                        //		public final static int		opc_fdiv = 110;
    null,                        //		public final static int		opc_ddiv = 111;
    {"java/lang/ArithmeticException"},                        //		public final static int		opc_irem = 112;
    {"java/lang/ArithmeticException"},                        //		public final static int		opc_lrem = 113;
    null,                        //		public final static int		opc_frem = 114;
    null,                        //		public final static int		opc_drem = 115;
    null,                        //		public final static int		opc_ineg = 116;
    null,                        //		public final static int		opc_lneg = 117;
    null,                        //		public final static int		opc_fneg = 118;
    null,                        //		public final static int		opc_dneg = 119;
    null,                        //		public final static int		opc_ishl = 120;
    null,                        //		public final static int		opc_lshl = 121;
    null,                        //		public final static int		opc_ishr = 122;
    null,                        //		public final static int		opc_lshr = 123;
    null,                        //		public final static int		opc_iushr = 124;
    null,                        //		public final static int		opc_lushr = 125;
    null,                        //		public final static int		opc_iand = 126;
    null,                        //		public final static int		opc_land = 127;
    null,                        //		public final static int		opc_ior = 128;
    null,                        //		public final static int		opc_lor = 129;
    null,                        //		public final static int		opc_ixor = 130;
    null,                        //		public final static int		opc_lxor = 131;
    null,                        //		public final static int		opc_iinc = 132;
    null,                        //		public final static int		opc_i2l = 133;
    null,                        //		public final static int		opc_i2f = 134;
    null,                        //		public final static int		opc_i2d = 135;
    null,                        //		public final static int		opc_l2i = 136;
    null,                        //		public final static int		opc_l2f = 137;
    null,                        //		public final static int		opc_l2d = 138;
    null,                        //		public final static int		opc_f2i = 139;
    null,                        //		public final static int		opc_f2l = 140;
    null,                        //		public final static int		opc_f2d = 141;
    null,                        //		public final static int		opc_d2i = 142;
    null,                        //		public final static int		opc_d2l = 143;
    null,                        //		public final static int		opc_d2f = 144;
    null,                        //		public final static int		opc_i2b = 145;
    null,                        //		public final static int		opc_i2c = 146;
    null,                        //		public final static int		opc_i2s = 147;
    null,                        //		public final static int		opc_lcmp = 148;
    null,                        //		public final static int		opc_fcmpl = 149;
    null,                        //		public final static int		opc_fcmpg = 150;
    null,                        //		public final static int		opc_dcmpl = 151;
    null,                        //		public final static int		opc_dcmpg = 152;
    null,                        //		public final static int		opc_ifeq = 153;
    null,                        //		public final static int		opc_ifne = 154;
    null,                        //		public final static int		opc_iflt = 155;
    null,                        //		public final static int		opc_ifge = 156;
    null,                        //		public final static int		opc_ifgt = 157;
    null,                        //		public final static int		opc_ifle = 158;
    null,                        //		public final static int		opc_if_icmpeq = 159;
    null,                        //		public final static int		opc_if_icmpne = 160;
    null,                        //		public final static int		opc_if_icmplt = 161;
    null,                        //		public final static int		opc_if_icmpge = 162;
    null,                        //		public final static int		opc_if_icmpgt = 163;
    null,                        //		public final static int		opc_if_icmple = 164;
    null,                        //		public final static int		opc_if_acmpeq = 165;
    null,                        //		public final static int		opc_if_acmpne = 166;
    null,                        //		public final static int		opc_goto = 167;
    null,                        //		public final static int		opc_jsr = 168;
    null,                        //		public final static int		opc_ret = 169;
    null,                        //		public final static int		opc_tableswitch = 170;
    null,                        //		public final static int		opc_lookupswitch = 171;
    {"java/lang/IllegalMonitorStateException"},                        //		public final static int		opc_ireturn = 172;
    {"java/lang/IllegalMonitorStateException"},                        //		public final static int		opc_lreturn = 173;
    {"java/lang/IllegalMonitorStateException"},                        //		public final static int		opc_freturn = 174;
    {"java/lang/IllegalMonitorStateException"},                        //		public final static int		opc_dreturn = 175;
    {"java/lang/IllegalMonitorStateException"},                        //		public final static int		opc_areturn = 176;
    {"java/lang/IllegalMonitorStateException"},                        //		public final static int		opc_return = 177;
    null,                        //		public final static int		opc_getstatic = 178;
    null,                        //		public final static int		opc_putstatic = 179;
    {"java/lang/NullPointerException"},                        //		public final static int		opc_getfield = 180;
    {"java/lang/NullPointerException"},                        //		public final static int		opc_putfield = 181;
    {"java/lang/NullPointerException", "java/lang/AbstractMethodError", "java/lang/UnsatisfiedLinkError"},
    //		public final static int		opc_invokevirtual = 182;
    {"java/lang/NullPointerException", "java/lang/UnsatisfiedLinkError"},
    //		public final static int		opc_invokespecial = 183;
    {"java/lang/UnsatisfiedLinkError"},                        //		public final static int		opc_invokestatic = 184;
    {"java/lang/NullPointerException", "java/lang/IncompatibleClassChangeError", "java/lang/IllegalAccessError",
      "java/lang/java/lang/AbstractMethodError", "java/lang/UnsatisfiedLinkError"},
    //		public final static int		opc_invokeinterface = 185;
    null,                        //		public final static int		opc_xxxunusedxxx = 186;
    null,                        //		public final static int		opc_new = 187;
    {"java/lang/NegativeArraySizeException"},                        //		public final static int		opc_newarray = 188;
    {"java/lang/NegativeArraySizeException"},                        //		public final static int		opc_anewarray = 189;
    {"java/lang/NullPointerException"},                        //		public final static int		opc_arraylength = 190;
    {"java/lang/NullPointerException", "java/lang/IllegalMonitorStateException"},
    //		public final static int		opc_athrow = 191;
    {"java/lang/ClassCastException"},                        //		public final static int		opc_checkcast = 192;
    null,                        //		public final static int		opc_instanceof = 193;
    {"java/lang/NullPointerException"},                        //		public final static int		opc_monitorenter = 194;
    {"java/lang/NullPointerException", "java/lang/IllegalMonitorStateException"},
    //		public final static int		opc_monitorexit = 195;
    null,                        //		public final static int		opc_wide = 196;
    {"java/lang/NegativeArraySizeException"},                        //		public final static int		opc_multianewarray = 197;
    null,                        //		public final static int		opc_ifnull = 198;
    null,                        //		public final static int		opc_ifnonnull = 199;
    null,                        //		public final static int		opc_goto_w = 200;
    null,                        //		public final static int		opc_jsr_w = 201;
  };


  public static boolean instanceOf(StructContext context, String valclass, String refclass) {

    if (valclass.equals(refclass)) {
      return true;
    }

    StructClass cl = context.getClass(valclass);
    if (cl == null) {
      return false;
    }

    if (cl.superClass != null && instanceOf(context, cl.superClass.getString(), refclass)) {
      return true;
    }

    int[] interfaces = cl.getInterfaces();
    for (int i = 0; i < interfaces.length; i++) {
      String intfc = cl.getPool().getPrimitiveConstant(interfaces[i]).getString();

      if (instanceOf(context, intfc, refclass)) {
        return true;
      }
    }

    return false;
  }


  public static String[] getRuntimeExceptions(Instruction instr) {
    return runtime_exceptions[instr.opcode];
  }
}
