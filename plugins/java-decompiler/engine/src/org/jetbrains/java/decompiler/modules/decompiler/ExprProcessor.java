// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.main.CancellationManager;
import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectNode;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper.FinallyPathWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;
import org.jetbrains.java.decompiler.modules.decompiler.typeann.TypeAnnotationWriteHelper;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructTypePathEntry;
import org.jetbrains.java.decompiler.struct.attr.StructBootstrapMethodsAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;
import org.jetbrains.java.decompiler.struct.consts.PooledConstant;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.Type;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.*;
import java.util.stream.Collectors;

public class ExprProcessor implements CodeConstants {
  @SuppressWarnings("SpellCheckingInspection")
  public static final String UNDEFINED_TYPE_STRING = "<undefinedtype>";
  public static final String UNKNOWN_TYPE_STRING = "<unknown>";
  public static final String NULL_TYPE_STRING = "<null>";

  private static final Map<Integer, Integer> functionMap = Map.of(
    opc_arraylength, FunctionExprent.FUNCTION_ARRAY_LENGTH,
    opc_checkcast, FunctionExprent.FUNCTION_CAST,
    opc_instanceof, FunctionExprent.FUNCTION_INSTANCEOF
  );

  private static final VarType[] constants = {
    VarType.VARTYPE_INT, VarType.VARTYPE_FLOAT, VarType.VARTYPE_LONG, VarType.VARTYPE_DOUBLE, VarType.VARTYPE_CLASS, VarType.VARTYPE_STRING
  };

  private static final VarType[] varTypes = {
    VarType.VARTYPE_INT, VarType.VARTYPE_LONG, VarType.VARTYPE_FLOAT, VarType.VARTYPE_DOUBLE, VarType.VARTYPE_OBJECT
  };

  private static final VarType[] arrTypes = {
    VarType.VARTYPE_INT, VarType.VARTYPE_LONG, VarType.VARTYPE_FLOAT, VarType.VARTYPE_DOUBLE, VarType.VARTYPE_OBJECT,
    VarType.VARTYPE_BOOLEAN, VarType.VARTYPE_CHAR, VarType.VARTYPE_SHORT
  };

  private static final int[] func1 = {
    FunctionExprent.FUNCTION_ADD, FunctionExprent.FUNCTION_SUB, FunctionExprent.FUNCTION_MUL, FunctionExprent.FUNCTION_DIV,
    FunctionExprent.FUNCTION_REM
  };
  private static final int[] func2 = {
    FunctionExprent.FUNCTION_SHL, FunctionExprent.FUNCTION_SHR, FunctionExprent.FUNCTION_USHR, FunctionExprent.FUNCTION_AND,
    FunctionExprent.FUNCTION_OR, FunctionExprent.FUNCTION_XOR
  };
  private static final int[] func3 = {
    FunctionExprent.FUNCTION_I2L, FunctionExprent.FUNCTION_I2F, FunctionExprent.FUNCTION_I2D, FunctionExprent.FUNCTION_L2I,
    FunctionExprent.FUNCTION_L2F, FunctionExprent.FUNCTION_L2D, FunctionExprent.FUNCTION_F2I, FunctionExprent.FUNCTION_F2L,
    FunctionExprent.FUNCTION_F2D, FunctionExprent.FUNCTION_D2I, FunctionExprent.FUNCTION_D2L, FunctionExprent.FUNCTION_D2F,
    FunctionExprent.FUNCTION_I2B, FunctionExprent.FUNCTION_I2C, FunctionExprent.FUNCTION_I2S
  };
  private static final int[] func4 = {
    FunctionExprent.FUNCTION_LCMP, FunctionExprent.FUNCTION_FCMPL, FunctionExprent.FUNCTION_FCMPG, FunctionExprent.FUNCTION_DCMPL,
    FunctionExprent.FUNCTION_DCMPG
  };
  private static final int[] func5 = {
    IfExprent.IF_EQ, IfExprent.IF_NE, IfExprent.IF_LT, IfExprent.IF_GE, IfExprent.IF_GT, IfExprent.IF_LE
  };
  private static final int[] func6 = {
    IfExprent.IF_ICMPEQ, IfExprent.IF_ICMPNE, IfExprent.IF_ICMPLT, IfExprent.IF_ICMPGE, IfExprent.IF_ICMPGT, IfExprent.IF_ICMPLE,
    IfExprent.IF_ACMPEQ, IfExprent.IF_ACMPNE
  };
  private static final int[] func7 = {IfExprent.IF_NULL, IfExprent.IF_NONNULL};
  private static final int[] func8 = {MonitorExprent.MONITOR_ENTER, MonitorExprent.MONITOR_EXIT};

  private static final int[] arrTypeIds = {
    CodeConstants.TYPE_BOOLEAN, CodeConstants.TYPE_CHAR, CodeConstants.TYPE_FLOAT, CodeConstants.TYPE_DOUBLE,
    CodeConstants.TYPE_BYTE, CodeConstants.TYPE_SHORT, CodeConstants.TYPE_INT, CodeConstants.TYPE_LONG
  };

  private static final int[] negIfs = {
    IfExprent.IF_NE, IfExprent.IF_EQ, IfExprent.IF_GE, IfExprent.IF_LT, IfExprent.IF_LE, IfExprent.IF_GT, IfExprent.IF_NONNULL,
    IfExprent.IF_NULL, IfExprent.IF_ICMPNE, IfExprent.IF_ICMPEQ, IfExprent.IF_ICMPGE, IfExprent.IF_ICMPLT, IfExprent.IF_ICMPLE,
    IfExprent.IF_ICMPGT, IfExprent.IF_ACMPNE, IfExprent.IF_ACMPEQ
  };

  private static final String[] typeNames = {"byte", "char", "double", "float", "int", "long", "short", "boolean"};

  private static final String EMPTY_ENTRY_POINTS_KEY = "-";

  private final MethodDescriptor methodDescriptor;
  private final VarProcessor varProcessor;

  public ExprProcessor(MethodDescriptor md, VarProcessor varProc) {
    methodDescriptor = md;
    varProcessor = varProc;
  }

  public void processStatement(RootStatement root, StructClass cl) {
    CancellationManager cancellationManager = DecompilerContext.getCancellationManager();
    cancellationManager.checkCanceled();
    FlattenStatementsHelper flattenHelper = new FlattenStatementsHelper();
    DirectGraph dgraph = flattenHelper.buildDirectGraph(root);

    // collect finally entry points
    Set<String> setFinallyShortRangeEntryPoints = new HashSet<>();
    for (List<FinallyPathWrapper> wrappers : dgraph.mapShortRangeFinallyPaths.values()) {
      for (FinallyPathWrapper wrapper : wrappers) {
        setFinallyShortRangeEntryPoints.add(wrapper.entry);
      }
    }

    Set<String> setFinallyLongRangeEntryPaths = new HashSet<>();
    for (List<FinallyPathWrapper> wrappers : dgraph.mapLongRangeFinallyPaths.values()) {
      for (FinallyPathWrapper wrapper : wrappers) {
        setFinallyLongRangeEntryPaths.add(wrapper.source + "##" + wrapper.entry);
      }
    }

    Map<String, VarExprent> mapCatch = new HashMap<>();
    collectCatchVars(root, flattenHelper, mapCatch);

    Map<DirectNode, Map<String, PrimitiveExpressionList>> mapData = new HashMap<>();

    LinkedList<DirectNode> stack = new LinkedList<>();
    LinkedList<LinkedList<String>> stackEntryPoint = new LinkedList<>();

    stack.add(dgraph.first);
    stackEntryPoint.add(new LinkedList<>());

    Map<String, PrimitiveExpressionList> map = new HashMap<>();
    map.put(EMPTY_ENTRY_POINTS_KEY, new PrimitiveExpressionList());
    mapData.put(dgraph.first, map);

    while (!stack.isEmpty()) {
      cancellationManager.checkCanceled();
      DirectNode node = stack.removeFirst();
      LinkedList<String> entryPoints = stackEntryPoint.removeFirst();

      PrimitiveExpressionList data;
      if (mapCatch.containsKey(node.id)) {
        data = getExpressionData(mapCatch.get(node.id));
      }
      else {
        data = mapData.get(node).get(buildEntryPointKey(entryPoints));
      }

      BasicBlockStatement block = node.block;
      if (block != null) {
        processBlock(block, data, cl);
        block.setExprents(data.getExpressions());
      }

      String currentEntrypoint = entryPoints.isEmpty() ? null : entryPoints.getLast();

      for (DirectNode nd : node.successors) {
        cancellationManager.checkCanceled();
        boolean isSuccessor = true;

        if (currentEntrypoint != null && dgraph.mapLongRangeFinallyPaths.containsKey(node.id)) {
          isSuccessor = false;
          for (FinallyPathWrapper wrapper : dgraph.mapLongRangeFinallyPaths.get(node.id)) {
            if (wrapper.source.equals(currentEntrypoint) && wrapper.destination.equals(nd.id)) {
              isSuccessor = true;
              break;
            }
          }
        }

        if (isSuccessor) {
          Map<String, PrimitiveExpressionList> successorMap = mapData.computeIfAbsent(nd, k -> new HashMap<>());
          LinkedList<String> nodeEntryPoints = new LinkedList<>(entryPoints);

          if (setFinallyLongRangeEntryPaths.contains(node.id + "##" + nd.id)) {
            nodeEntryPoints.addLast(node.id);
          }
          else if (!setFinallyShortRangeEntryPoints.contains(nd.id) && dgraph.mapLongRangeFinallyPaths.containsKey(node.id)) {
            nodeEntryPoints.removeLast(); // currentEntrypoint should not be null at this point
          }

          // handling of entry point loops
          int successorEntryIndex = nodeEntryPoints.indexOf(nd.id);
          if (successorEntryIndex >= 0) {
            // we are in a loop (e.g. continue in a 'finally' block): drop all entry points in the list beginning with successor index
            for (int elementsToRemove = nodeEntryPoints.size() - successorEntryIndex; elementsToRemove > 0; elementsToRemove--) {
              nodeEntryPoints.removeLast();
            }
          }

          String nodeEntryKey = buildEntryPointKey(nodeEntryPoints);
          if (!successorMap.containsKey(nodeEntryKey)) {
            successorMap.put(nodeEntryKey, data.copy());
            stack.add(nd);
            stackEntryPoint.add(nodeEntryPoints);
          }
        }
      }
    }

    initStatementExprents(root);
  }

  // FIXME: Ugly code, to be rewritten. A tuple class is needed.
  private static String buildEntryPointKey(LinkedList<String> entryPoints) {
    if (entryPoints.isEmpty()) {
      return EMPTY_ENTRY_POINTS_KEY;
    }
    else if (entryPoints.size() == 1) {
      return entryPoints.getFirst();
    }
    else {
      return String.join(":", entryPoints);
    }
  }

  private static void collectCatchVars(Statement stat, FlattenStatementsHelper flatthelper, Map<String, VarExprent> map) {
    List<VarExprent> lst = null;

    if (stat.type == StatementType.CATCH_ALL) {
      CatchAllStatement catchall = (CatchAllStatement)stat;
      if (!catchall.isFinally()) {
        lst = catchall.getVars();
      }
    }
    else if (stat.type == StatementType.TRY_CATCH) {
      lst = ((CatchStatement)stat).getVars();
    }

    if (lst != null) {
      for (int i = 1; i < stat.getStats().size(); i++) {
        map.put(flatthelper.getMapDestinationNodes().get(stat.getStats().get(i).id)[0], lst.get(i - 1));
      }
    }

    for (Statement st : stat.getStats()) {
      collectCatchVars(st, flatthelper, map);
    }
  }

  private static void initStatementExprents(Statement stat) {
    stat.initExprents();

    for (Statement st : stat.getStats()) {
      initStatementExprents(st);
    }
  }

  public void processBlock(BasicBlockStatement stat, PrimitiveExpressionList data, StructClass cl) {
    ConstantPool pool = cl.getPool();
    StructBootstrapMethodsAttribute bootstrap = cl.getAttribute(StructGeneralAttribute.ATTRIBUTE_BOOTSTRAP_METHODS);
    BasicBlock block = stat.getBlock();
    ExpressionStack stack = data.getStack();
    List<Exprent> exprList = data.getExpressions();
    InstructionSequence seq = block.getSeq();

    for (int i = 0; i < seq.length(); i++) {
      Instruction instr = seq.getInstr(i);
      Integer offset = block.getOriginalOffset(i);
      Set<Integer> offsets = offset >= 0 ? Set.of(offset) : null;

      switch (instr.opcode) {
        case opc_aconst_null:
          pushEx(stack, exprList, new ConstExprent(VarType.VARTYPE_NULL, null, offsets));
          break;
        case opc_bipush:
        case opc_sipush:
          pushEx(stack, exprList, new ConstExprent(instr.operand(0), true, offsets));
          break;
        case opc_lconst_0:
        case opc_lconst_1:
          pushEx(stack, exprList, new ConstExprent(VarType.VARTYPE_LONG, (long)(instr.opcode - opc_lconst_0), offsets));
          break;
        case opc_fconst_0:
        case opc_fconst_1:
        case opc_fconst_2:
          pushEx(stack, exprList, new ConstExprent(VarType.VARTYPE_FLOAT, (float)(instr.opcode - opc_fconst_0), offsets));
          break;
        case opc_dconst_0:
        case opc_dconst_1:
          pushEx(stack, exprList, new ConstExprent(VarType.VARTYPE_DOUBLE, (double)(instr.opcode - opc_dconst_0), offsets));
          break;
        case opc_ldc:
        case opc_ldc_w:
        case opc_ldc2_w: {
          PooledConstant cn = pool.getConstant(instr.operand(0));
          if (cn instanceof PrimitiveConstant) {
            pushEx(stack, exprList, new ConstExprent(constants[cn.type - CONSTANT_Integer], ((PrimitiveConstant)cn).value, offsets));
          }
          else if (cn instanceof LinkConstant) {
            //TODO: for now treat Links as Strings
            pushEx(stack, exprList, new ConstExprent(VarType.VARTYPE_STRING, ((LinkConstant)cn).elementname, offsets));
          }
          break;
        }
        case opc_iload:
        case opc_lload:
        case opc_fload:
        case opc_dload:
        case opc_aload:
          pushEx(stack, exprList, new VarExprent(instr.operand(0), varTypes[instr.opcode - opc_iload], varProcessor, offset));
          break;
        case opc_iaload:
        case opc_laload:
        case opc_faload:
        case opc_daload:
        case opc_aaload:
        case opc_baload:
        case opc_caload:
        case opc_saload: {
          Exprent index = stack.pop();
          Exprent arr = stack.pop();
          VarType type = instr.opcode == opc_laload ? VarType.VARTYPE_LONG : instr.opcode == opc_daload ? VarType.VARTYPE_DOUBLE : null;
          pushEx(stack, exprList, new ArrayExprent(arr, index, arrTypes[instr.opcode - opc_iaload], offsets), type);
          break;
        }
        case opc_istore:
        case opc_lstore:
        case opc_fstore:
        case opc_dstore:
        case opc_astore: {
          Exprent value = stack.pop();
          int varIndex = instr.operand(0);
          VarExprent left = new VarExprent(varIndex, varTypes[instr.opcode - opc_istore], varProcessor, nextMeaningfulOffset(block, i));
          exprList.add(new AssignmentExprent(left, value, offsets));
          break;
        }
        case opc_iastore:
        case opc_lastore:
        case opc_fastore:
        case opc_dastore:
        case opc_aastore:
        case opc_bastore:
        case opc_castore:
        case opc_sastore: {
          Exprent value = stack.pop();
          Exprent index = stack.pop();
          Exprent array = stack.pop();
          ArrayExprent left = new ArrayExprent(array, index, arrTypes[instr.opcode - opc_iastore], offsets);
          exprList.add(new AssignmentExprent(left, value, offsets));
          break;
        }
        case opc_iadd:
        case opc_ladd:
        case opc_fadd:
        case opc_dadd:
        case opc_isub:
        case opc_lsub:
        case opc_fsub:
        case opc_dsub:
        case opc_imul:
        case opc_lmul:
        case opc_fmul:
        case opc_dmul:
        case opc_idiv:
        case opc_ldiv:
        case opc_fdiv:
        case opc_ddiv:
        case opc_irem:
        case opc_lrem:
        case opc_frem:
        case opc_drem:
          pushEx(stack, exprList, new FunctionExprent(func1[(instr.opcode - opc_iadd) / 4], stack, offsets));
          break;
        case opc_ishl:
        case opc_lshl:
        case opc_ishr:
        case opc_lshr:
        case opc_iushr:
        case opc_lushr:
        case opc_iand:
        case opc_land:
        case opc_ior:
        case opc_lor:
        case opc_ixor:
        case opc_lxor:
          pushEx(stack, exprList, new FunctionExprent(func2[(instr.opcode - opc_ishl) / 2], stack, offsets));
          break;
        case opc_ineg:
        case opc_lneg:
        case opc_fneg:
        case opc_dneg:
          pushEx(stack, exprList, new FunctionExprent(FunctionExprent.FUNCTION_NEG, stack, offsets));
          break;
        case opc_iinc: {
          VarExprent varExpr = new VarExprent(instr.operand(0), VarType.VARTYPE_INT, varProcessor);
          int type = instr.operand(1) < 0 ? FunctionExprent.FUNCTION_SUB : FunctionExprent.FUNCTION_ADD;
          List<Exprent> operands = Arrays.asList(varExpr.copy(), new ConstExprent(VarType.VARTYPE_INT, Math.abs(instr.operand(1)), null));
          exprList.add(new AssignmentExprent(varExpr, new FunctionExprent(type, operands, offsets), offsets));
          break;
        }
        case opc_i2l:
        case opc_i2f:
        case opc_i2d:
        case opc_l2i:
        case opc_l2f:
        case opc_l2d:
        case opc_f2i:
        case opc_f2l:
        case opc_f2d:
        case opc_d2i:
        case opc_d2l:
        case opc_d2f:
        case opc_i2b:
        case opc_i2c:
        case opc_i2s:
          pushEx(stack, exprList, new FunctionExprent(func3[instr.opcode - opc_i2l], stack, offsets));
          break;
        case opc_lcmp:
        case opc_fcmpl:
        case opc_fcmpg:
        case opc_dcmpl:
        case opc_dcmpg:
          pushEx(stack, exprList, new FunctionExprent(func4[instr.opcode - opc_lcmp], stack, offsets));
          break;
        case opc_ifeq:
        case opc_ifne:
        case opc_iflt:
        case opc_ifge:
        case opc_ifgt:
        case opc_ifle:
          exprList.add(new IfExprent(negIfs[func5[instr.opcode - opc_ifeq]], stack, offsets));
          break;
        case opc_if_icmpeq:
        case opc_if_icmpne:
        case opc_if_icmplt:
        case opc_if_icmpge:
        case opc_if_icmpgt:
        case opc_if_icmple:
        case opc_if_acmpeq:
        case opc_if_acmpne:
          exprList.add(new IfExprent(negIfs[func6[instr.opcode - opc_if_icmpeq]], stack, offsets));
          break;
        case opc_ifnull:
        case opc_ifnonnull:
          exprList.add(new IfExprent(negIfs[func7[instr.opcode - opc_ifnull]], stack, offsets));
          break;
        case opc_tableswitch:
        case opc_lookupswitch:
          exprList.add(new SwitchExprent(stack.pop(), offsets));
          break;
        case opc_ireturn:
        case opc_lreturn:
        case opc_freturn:
        case opc_dreturn:
        case opc_areturn:
        case opc_return:
        case opc_athrow:
          exprList.add(new ExitExprent(instr.opcode == opc_athrow ? ExitExprent.EXIT_THROW : ExitExprent.EXIT_RETURN,
                                       instr.opcode == opc_return ? null : stack.pop(),
                                       instr.opcode == opc_athrow ? null : methodDescriptor.ret,
                                       offsets));
          break;
        case opc_monitorenter:
        case opc_monitorexit:
          exprList.add(new MonitorExprent(func8[instr.opcode - opc_monitorenter], stack.pop(), offsets));
          break;
        case opc_checkcast:
        case opc_instanceof:
          stack.push(new ConstExprent(new VarType(pool.getPrimitiveConstant(instr.operand(0)).getString(), true), null, null));
        case opc_arraylength:
          pushEx(stack, exprList, new FunctionExprent(functionMap.get(instr.opcode), stack, offsets));
          break;
        case opc_getstatic:
        case opc_getfield:
          pushEx(stack, exprList,
                 new FieldExprent(pool.getLinkConstant(instr.operand(0)), instr.opcode == opc_getstatic ? null : stack.pop(), offsets));
          break;
        case opc_putstatic:
        case opc_putfield:
          Exprent valfield = stack.pop();
          Exprent exprfield =
            new FieldExprent(pool.getLinkConstant(instr.operand(0)), instr.opcode == opc_putstatic ? null : stack.pop(), offsets);
          exprList.add(new AssignmentExprent(exprfield, valfield, offsets));
          break;
        case opc_invokevirtual:
        case opc_invokespecial:
        case opc_invokestatic:
        case opc_invokeinterface:
        case opc_invokedynamic:
          if (instr.opcode != opc_invokedynamic || instr.bytecodeVersion >= CodeConstants.BYTECODE_JAVA_7) {
            LinkConstant invoke_constant = pool.getLinkConstant(instr.operand(0));

            List<PooledConstant> bootstrap_arguments = null;
            if (instr.opcode == opc_invokedynamic && bootstrap != null) {
              bootstrap_arguments = bootstrap.getMethodArguments(invoke_constant.index1);
            }

            InvocationExprent exprinv = new InvocationExprent(instr.opcode, invoke_constant, bootstrap_arguments, stack, offsets);
            if (exprinv.getDescriptor().ret.getType() == CodeConstants.TYPE_VOID) {
              exprList.add(exprinv);
            }
            else {
              pushEx(stack, exprList, exprinv);
            }
          }
          break;
        case opc_new:
        case opc_anewarray:
        case opc_multianewarray:
          int dimensions = (instr.opcode == opc_new) ? 0 : (instr.opcode == opc_anewarray) ? 1 : instr.operand(1);
          VarType arrType = new VarType(pool.getPrimitiveConstant(instr.operand(0)).getString(), true);
          if (instr.opcode != opc_multianewarray) {
            arrType = arrType.resizeArrayDim(arrType.getArrayDim() + dimensions);
          }
          pushEx(stack, exprList, new NewExprent(arrType, stack, dimensions, offsets));
          break;
        case opc_newarray:
          pushEx(stack, exprList, new NewExprent(new VarType(arrTypeIds[instr.operand(0) - 4], 1), stack, 1, offsets));
          break;
        case opc_dup:
          pushEx(stack, exprList, stack.getByOffset(-1).copy());
          break;
        case opc_dup_x1:
          insertByOffsetEx(-2, stack, exprList, -1);
          break;
        case opc_dup_x2:
          if (stack.getByOffset(-2).getExprType().getStackSize() == 2) {
            insertByOffsetEx(-2, stack, exprList, -1);
          }
          else {
            insertByOffsetEx(-3, stack, exprList, -1);
          }
          break;
        case opc_dup2:
          if (stack.getByOffset(-1).getExprType().getStackSize() == 2) {
            pushEx(stack, exprList, stack.getByOffset(-1).copy());
          }
          else {
            pushEx(stack, exprList, stack.getByOffset(-2).copy());
            pushEx(stack, exprList, stack.getByOffset(-2).copy());
          }
          break;
        case opc_dup2_x1:
          if (stack.getByOffset(-1).getExprType().getStackSize() == 2) {
            insertByOffsetEx(-2, stack, exprList, -1);
          }
          else {
            insertByOffsetEx(-3, stack, exprList, -2);
            insertByOffsetEx(-3, stack, exprList, -1);
          }
          break;
        case opc_dup2_x2:
          if (stack.getByOffset(-1).getExprType().getStackSize() == 2) {
            if (stack.getByOffset(-2).getExprType().getStackSize() == 2) {
              insertByOffsetEx(-2, stack, exprList, -1);
            }
            else {
              insertByOffsetEx(-3, stack, exprList, -1);
            }
          }
          else {
            if (stack.getByOffset(-3).getExprType().getStackSize() == 2) {
              insertByOffsetEx(-3, stack, exprList, -2);
              insertByOffsetEx(-3, stack, exprList, -1);
            }
            else {
              insertByOffsetEx(-4, stack, exprList, -2);
              insertByOffsetEx(-4, stack, exprList, -1);
            }
          }
          break;
        case opc_swap:
          insertByOffsetEx(-2, stack, exprList, -1);
          stack.pop();
          break;
        case opc_pop:
          stack.pop();
          break;
        case opc_pop2:
          if (stack.getByOffset(-1).getExprType().getStackSize() == 1) {
            // Since value at the top of the stack is a value of category 1 (JVMS9 2.11.1)
            // we should remove one more item from the stack.
            // See JVMS9 pop2 chapter.
            stack.pop();
          }
          stack.pop();
          break;
      }
    }
  }

  private static int nextMeaningfulOffset(BasicBlock block, int index) {
    InstructionSequence seq = block.getSeq();
    while (++index < seq.length()) {
      switch (seq.getInstr(index).opcode) {
        case opc_nop, opc_istore, opc_lstore, opc_fstore, opc_dstore, opc_astore -> {
          continue;
        }
      }
      return block.getOriginalOffset(index);
    }

    List<BasicBlock> successors = block.getSuccessors();
    if (successors.size() == 1) {
      return successors.get(0).getOriginalOffset(0);
    }

    return -1;
  }

  private void pushEx(ExpressionStack stack, List<Exprent> exprlist, Exprent exprent) {
    pushEx(stack, exprlist, exprent, null);
  }

  private void pushEx(ExpressionStack stack, List<Exprent> exprlist, Exprent exprent, VarType vartype) {
    int varindex = VarExprent.STACK_BASE + stack.size();
    VarExprent var = new VarExprent(varindex, vartype == null ? exprent.getExprType() : vartype, varProcessor);
    var.setStack(true);

    exprlist.add(new AssignmentExprent(var, exprent, null));
    stack.push(var.copy());
  }

  private void insertByOffsetEx(int offset, ExpressionStack stack, List<Exprent> exprlist, int copyoffset) {

    int base = VarExprent.STACK_BASE + stack.size();

    LinkedList<VarExprent> lst = new LinkedList<>();

    for (int i = -1; i >= offset; i--) {
      Exprent varex = stack.pop();
      VarExprent varnew = new VarExprent(base + i + 1, varex.getExprType(), varProcessor);
      varnew.setStack(true);
      exprlist.add(new AssignmentExprent(varnew, varex, null));
      lst.add(0, (VarExprent)varnew.copy());
    }

    Exprent exprent = lst.get(lst.size() + copyoffset).copy();
    VarExprent var = new VarExprent(base + offset, exprent.getExprType(), varProcessor);
    var.setStack(true);
    exprlist.add(new AssignmentExprent(var, exprent, null));
    lst.add(0, (VarExprent)var.copy());

    for (VarExprent expr : lst) {
      stack.push(expr);
    }
  }

  public static String getTypeName(VarType type, List<TypeAnnotationWriteHelper> typePathWriteHelper) {
    return getTypeName(type, true, typePathWriteHelper);
  }

  public static String getTypeName(VarType type, boolean getShort, List<TypeAnnotationWriteHelper> typeAnnWriteHelpers) {
    int tp = type.getType();
    StringBuilder sb = new StringBuilder();
    typeAnnWriteHelpers = writeTypeAnnotationBeforeType(type, sb, typeAnnWriteHelpers);
    if (tp <= CodeConstants.TYPE_BOOLEAN) {
      sb.append(typeNames[tp]);
      return sb.toString();
    }
    else if (tp == CodeConstants.TYPE_UNKNOWN) {
      sb.append(UNKNOWN_TYPE_STRING);
      return sb.toString(); // INFO: should not occur
    }
    else if (tp == CodeConstants.TYPE_NULL) {
      sb.append(NULL_TYPE_STRING);
      return sb.toString(); // INFO: should not occur
    }
    else if (tp == CodeConstants.TYPE_VOID) {
      sb.append("void");
      return sb.toString();
    }
    else if (tp == CodeConstants.TYPE_OBJECT) {
      String ret;
      if (getShort) {
        ret = DecompilerContext.getImportCollector().getNestedName(type.getValue());
      } else {
        ret = buildJavaClassName(type.getValue());
      }
      if (ret == null) {
        return UNDEFINED_TYPE_STRING; // FIXME: a warning should be logged
      }
      List<String> nestedTypes = Arrays.asList(ret.split("\\."));
      writeNestedClass(sb, type, nestedTypes, typeAnnWriteHelpers);
      popNestedTypeAnnotation(typeAnnWriteHelpers);
      return sb.toString();
    }

    throw new RuntimeException("invalid type");
  }

  public static List<TypeAnnotationWriteHelper> writeTypeAnnotationBeforeType(
    Type type,
    StringBuilder sb,
    List<TypeAnnotationWriteHelper> typeAnnWriteHelpers
  ) {
    return typeAnnWriteHelpers.stream().filter(typeAnnWriteHelper -> { // TODO remove duplicate
      if (typeAnnWriteHelper.getAnnotation().isWrittenBeforeType(type)) {
        typeAnnWriteHelper.writeTo(sb);
        return false;
      }
      StructTypePathEntry pathEntry = typeAnnWriteHelper.getPaths().peek();
      if (pathEntry != null
          && pathEntry.getTypePathEntryKind() == StructTypePathEntry.Kind.NESTED.getId()
          && type.isAnnotatable()
      ) typeAnnWriteHelper.getPaths().pop();
      return true;
    }).collect(Collectors.toList());
  }

  public static List<TypeAnnotationWriteHelper> writeNestedClass(
    StringBuilder sb,
    Type type,
    List<String> nestedTypes,
    List<TypeAnnotationWriteHelper> typeAnnWriteHelpers
  ) {
    List<ClassesProcessor.ClassNode> enclosingClasses = enclosingClassList();
    StringBuilder curPathBldr = new StringBuilder(type.getValue().substring(0, type.getValue().lastIndexOf('/') + 1));
    for (int i = 0; i < nestedTypes.size(); i++) {
      String nestedType = nestedTypes.get(i);
      boolean shouldWrite = true;
      if (!enclosingClasses.isEmpty() && i != nestedTypes.size() - 1) {
        String enclosingType = enclosingClasses.remove(0).simpleName;
        shouldWrite = !nestedType.equals(enclosingType);
      }
      if (i == 0) { // first annotation can be written already
        if (!sb.toString().isEmpty()) shouldWrite = true; // write if annotation exists
      } else {
        if (canWriteNestedTypeAnnotation(curPathBldr + nestedType + '$', nestedTypes.subList(i + 1, nestedTypes.size()))) {
          List<TypeAnnotationWriteHelper> notWrittenTypeAnnotations = writeNestedTypeAnnotations(sb, typeAnnWriteHelpers);
          shouldWrite |= (notWrittenTypeAnnotations.size() != typeAnnWriteHelpers.size());
          typeAnnWriteHelpers = notWrittenTypeAnnotations;
          if (i != nestedTypes.size() - 1) popNestedTypeAnnotation(typeAnnWriteHelpers);
        }
      }
      if (shouldWrite) {
        sb.append(nestedType);
        curPathBldr.append(nestedType);
        if (i != nestedTypes.size() - 1) {
          curPathBldr.append('$');
          sb.append('.');
        }
      }
    }
    return typeAnnWriteHelpers;
  }

  /**
   * Nested type annotations can only be written when all types on the right of the currently annotated type don't reference a static class.
   */
  public static boolean canWriteNestedTypeAnnotation(String curPath, List<String> nestedTypes) {
    if (nestedTypes.isEmpty()) return true;
    String fullName = curPath + nestedTypes.get(0);
    ClassesProcessor.ClassNode classNode = DecompilerContext.getClassProcessor().getMapRootClasses().get(fullName);
    if (classNode == null) return false;
    return (classNode.access & CodeConstants.ACC_STATIC) == 0 && canWriteNestedTypeAnnotation(fullName + "$", nestedTypes.subList(1, nestedTypes.size()));
  }

  public static List<ClassesProcessor.ClassNode> enclosingClassList() {
    ClassesProcessor.ClassNode enclosingClass = (ClassesProcessor.ClassNode) DecompilerContext.getProperty(
      DecompilerContext.CURRENT_CLASS_NODE
    );
    List<ClassesProcessor.ClassNode> enclosingClassList = new ArrayList<>(List.of(enclosingClass));
    while (enclosingClass.parent != null) {
      enclosingClass = enclosingClass.parent;
      enclosingClassList.add(0, enclosingClass);
    }
    return enclosingClassList.stream()
      .filter(classNode -> classNode.type != ClassesProcessor.ClassNode.CLASS_ANONYMOUS &&
                           classNode.type != ClassesProcessor.ClassNode.CLASS_LAMBDA
      ).collect(Collectors.toList());
  }

  public static List<TypeAnnotationWriteHelper> writeNestedTypeAnnotations(
    StringBuilder sb,
    List<TypeAnnotationWriteHelper> typeAnnWriteHelpers
  ) {
    return typeAnnWriteHelpers.stream().filter(typeAnnWriteHelper -> {
      StructTypePathEntry path = typeAnnWriteHelper.getPaths().peek();
      if (path == null) {
        typeAnnWriteHelper.writeTo(sb);
        return false;
      }
      return true;
    }).collect(Collectors.toList());
  }

  /**
   * Pops the nested path entry of the type annotation helper stack. Should be called after writing a nested type.
   */
  public static void popNestedTypeAnnotation(List<TypeAnnotationWriteHelper> typeAnnWriteHelpers) {
    typeAnnWriteHelpers.forEach(typeAnnWriteHelper -> {
      StructTypePathEntry path = typeAnnWriteHelper.getPaths().peek();
      if (path != null && path.getTypePathEntryKind() == StructTypePathEntry.Kind.NESTED.getId()) {
        typeAnnWriteHelper.getPaths().pop();
      }
    });
  }

  public static String getCastTypeName(VarType type, List<TypeAnnotationWriteHelper> typePathWriteHelper) {
    return getCastTypeName(type, true, typePathWriteHelper);
  }

  public static String getCastTypeName(VarType type, boolean getShort, List<TypeAnnotationWriteHelper> typeAnnWriteHelpers) {
    List<TypeAnnotationWriteHelper> arrayTypeAnnWriteHelpers = arrayPath(type, typeAnnWriteHelpers);
    List<TypeAnnotationWriteHelper> nonArrayTypeAnnWriteHelpers = nonArrayPath(type, typeAnnWriteHelpers);
    StringBuilder sb = new StringBuilder(getTypeName(type, getShort, nonArrayTypeAnnWriteHelpers));
    writeArray(sb, type.getArrayDim(), arrayTypeAnnWriteHelpers);
    return sb.toString();
  }


  public static List<TypeAnnotationWriteHelper> arrayPath(Type type, List<TypeAnnotationWriteHelper> typeAnnWriteHelpers) {
    return typeAnnWriteHelpers.stream()
      .filter(typeAnnWriteHelper -> typeAnnWriteHelper.getPaths().size() < type.getArrayDim())
      .collect(Collectors.toList());
  }

  public static List<TypeAnnotationWriteHelper> nonArrayPath(Type type, List<TypeAnnotationWriteHelper> typeAnnWriteHelpers) {
    return typeAnnWriteHelpers.stream().filter(stack -> {
      boolean isArrayPath = stack.getPaths().size() < type.getArrayDim();
      if (stack.getPaths().size() > type.getArrayDim()) {
        for (int i = 0; i < type.getArrayDim(); i++) {
          stack.getPaths().poll(); // remove all trailing
        }
      }
      return !isArrayPath;
    }).collect(Collectors.toList());
  }


  public static void writeArray(StringBuilder sb, int arrayDim, List<TypeAnnotationWriteHelper> typeAnnWriteHelpers) {
    for (int i = 0; i < arrayDim; i++) {
      boolean firstIteration = true;
      for (TypeAnnotationWriteHelper typeAnnotationWriteHelper : typeAnnWriteHelpers) {
        if (i == typeAnnotationWriteHelper.getPaths().size()) {
          if (firstIteration) {
            sb.append(' ');
            firstIteration = false;
          }
          typeAnnotationWriteHelper.writeTo(sb);
        }
      }
      sb.append("[]");
    }
  }

  public static PrimitiveExpressionList getExpressionData(VarExprent var) {
    PrimitiveExpressionList prlst = new PrimitiveExpressionList();
    VarExprent vartmp = new VarExprent(VarExprent.STACK_BASE, var.getExprType(), var.getProcessor());
    vartmp.setStack(true);

    prlst.getExpressions().add(new AssignmentExprent(vartmp, var.copy(), null));
    prlst.getStack().push(vartmp.copy());
    return prlst;
  }

  public static boolean endsWithSemicolon(Exprent expr) {
    int type = expr.type;
    return !(type == Exprent.EXPRENT_SWITCH ||
             type == Exprent.EXPRENT_MONITOR ||
             type == Exprent.EXPRENT_IF ||
             (type == Exprent.EXPRENT_VAR && ((VarExprent)expr).isClassDef()));
  }

  private static void addDeletedGotoInstructionMapping(Statement stat, BytecodeMappingTracer tracer) {
    if (stat instanceof BasicBlockStatement) {
      BasicBlock block = ((BasicBlockStatement)stat).getBlock();
      List<Integer> offsets = block.getOriginalOffsets();
      if (!offsets.isEmpty() &&
          offsets.size() > block.getSeq().length()) { // some instructions have been deleted, but we still have offsets
        tracer.addMapping(offsets.get(offsets.size() - 1)); // add the last offset
      }
    }
  }

  public static TextBuffer jmpWrapper(Statement stat, int indent, boolean semicolon, BytecodeMappingTracer tracer) {
    TextBuffer buf = stat.toJava(indent, tracer);

    List<StatEdge> lstSuccs = stat.getSuccessorEdges(EdgeType.DIRECT_ALL);
    if (lstSuccs.size() == 1) {
      StatEdge edge = lstSuccs.get(0);
      if (edge.getType() != EdgeType.REGULAR && edge.explicit && edge.getDestination().type != StatementType.DUMMY_EXIT) {
        buf.appendIndent(indent);

        if (EdgeType.BREAK.equals(edge.getType())) {
          addDeletedGotoInstructionMapping(stat, tracer);
          buf.append("break");
        }
        else if (EdgeType.CONTINUE.equals(edge.getType())) {
          addDeletedGotoInstructionMapping(stat, tracer);
          buf.append("continue");
        }

        if (edge.labeled) {
          buf.append(" label").append(Integer.toString(edge.closure.id));
        }
        buf.append(";").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
      }
    }

    if (buf.length() == 0 && semicolon) {
      buf.appendIndent(indent).append(";").appendLineSeparator();
      tracer.incrementCurrentSourceLine();
    }

    return buf;
  }

  public static String buildJavaClassName(String name) {
    String res = name.replace('/', '.');

    if (res.contains("$")) { // attempt to invoke foreign member
      // classes correctly
      StructClass cl = DecompilerContext.getStructContext().getClass(name);
      if (cl == null || !cl.isOwn()) {
        res = res.replace('$', '.');
      }
    }

    return res;
  }

  public static TextBuffer listToJava(List<? extends Exprent> lst, int indent, BytecodeMappingTracer tracer) {
    if (lst == null || lst.isEmpty()) {
      return new TextBuffer();
    }

    TextBuffer buf = new TextBuffer();

    for (Exprent expr : lst) {
      if (buf.length() > 0 && expr.type == Exprent.EXPRENT_VAR && ((VarExprent)expr).isClassDef()) {
        // separates local class definition from previous statements
        buf.appendLineSeparator();
        tracer.incrementCurrentSourceLine();
      }

      TextBuffer content = expr.toJava(indent, tracer);

      if (content.length() > 0) {
        if (expr.type != Exprent.EXPRENT_VAR || !((VarExprent)expr).isClassDef()) {
          buf.appendIndent(indent);
        }
        buf.append(content);
        if (expr.type == Exprent.EXPRENT_MONITOR && ((MonitorExprent)expr).getMonType() == MonitorExprent.MONITOR_ENTER) {
          buf.append("{}"); // empty synchronized block
        }
        if (endsWithSemicolon(expr)) {
          buf.append(";");
        }
        buf.appendLineSeparator();
        tracer.incrementCurrentSourceLine();
      }
    }

    return buf;
  }

  public static ConstExprent getDefaultArrayValue(VarType arrType) {
    ConstExprent defaultVal;
    if (arrType.getType() == CodeConstants.TYPE_OBJECT || arrType.getArrayDim() > 0) {
      defaultVal = new ConstExprent(VarType.VARTYPE_NULL, null, null);
    }
    else if (arrType.getType() == CodeConstants.TYPE_FLOAT) {
      defaultVal = new ConstExprent(VarType.VARTYPE_FLOAT, 0f, null);
    }
    else if (arrType.getType() == CodeConstants.TYPE_LONG) {
      defaultVal = new ConstExprent(VarType.VARTYPE_LONG, 0L, null);
    }
    else if (arrType.getType() == CodeConstants.TYPE_DOUBLE) {
      defaultVal = new ConstExprent(VarType.VARTYPE_DOUBLE, 0d, null);
    }
    else { // integer types
      defaultVal = new ConstExprent(0, true, null);
    }
    return defaultVal;
  }

  public static boolean getCastedExprent(Exprent exprent,
                                         VarType leftType,
                                         TextBuffer buffer,
                                         int indent,
                                         boolean castNull,
                                         BytecodeMappingTracer tracer) {
    return getCastedExprent(exprent, leftType, buffer, indent, castNull, false, false, false, tracer);
  }

  public static boolean getCastedExprent(Exprent exprent,
                                         VarType leftType,
                                         TextBuffer buffer,
                                         int indent,
                                         boolean castNull,
                                         boolean castAlways,
                                         boolean castNarrowing,
                                         boolean unbox,
                                         BytecodeMappingTracer tracer) {

    if (unbox) {
      // "unbox" invocation parameters, e.g. 'byteSet.add((byte)123)' or 'new ShortContainer((short)813)'
      if (exprent.type == Exprent.EXPRENT_INVOCATION && ((InvocationExprent)exprent).isBoxingCall()) {
        InvocationExprent invocationExprent = (InvocationExprent)exprent;
        exprent = invocationExprent.getParameters().get(0);
        int paramType = invocationExprent.getDescriptor().params[0].getType();
        if (exprent.type == Exprent.EXPRENT_CONST && ((ConstExprent)exprent).getConstType().getType() != paramType) {
          leftType = new VarType(paramType);
        }
      }
    }

    VarType rightType = exprent.getExprType();

    boolean cast =
      castAlways ||
      (!leftType.isSuperset(rightType) && (rightType.equals(VarType.VARTYPE_OBJECT) || leftType.getType() != CodeConstants.TYPE_OBJECT)) ||
      (castNull && rightType.getType() == CodeConstants.TYPE_NULL && !UNDEFINED_TYPE_STRING.equals(getTypeName(leftType, Collections.emptyList()))) ||
      (castNarrowing && isIntConstant(exprent) && isNarrowedIntType(leftType));

    boolean quote = cast && exprent.getPrecedence() >= FunctionExprent.getPrecedence(FunctionExprent.FUNCTION_CAST);

    // cast instead to 'byte' / 'short' when int constant is used as a value for 'Byte' / 'Short'
    if (castNarrowing && exprent.type == Exprent.EXPRENT_CONST && !((ConstExprent) exprent).isNull()) {
      if (leftType.equals(VarType.VARTYPE_BYTE_OBJ)) {
        leftType = VarType.VARTYPE_BYTE;
      }
      else if (leftType.equals(VarType.VARTYPE_SHORT_OBJ)) {
        leftType = VarType.VARTYPE_SHORT;
      }
    }

    if (cast) buffer.append('(').append(ExprProcessor.getCastTypeName(leftType, Collections.emptyList())).append(')');

    if (quote) buffer.append('(');

    if (exprent.type == Exprent.EXPRENT_CONST) {
      ((ConstExprent) exprent).adjustConstType(leftType);
    }

    buffer.append(exprent.toJava(indent, tracer));

    if (quote) buffer.append(')');

    return cast;
  }

  private static boolean isIntConstant(Exprent exprent) {
    if (exprent.type == Exprent.EXPRENT_CONST) {
      switch (((ConstExprent)exprent).getConstType().getType()) {
        case CodeConstants.TYPE_BYTE, CodeConstants.TYPE_BYTECHAR, CodeConstants.TYPE_SHORT,
          CodeConstants.TYPE_SHORTCHAR, CodeConstants.TYPE_INT -> {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean isNarrowedIntType(VarType type) {
    return VarType.VARTYPE_INT.isStrictSuperset(type) || type.equals(VarType.VARTYPE_BYTE_OBJ) || type.equals(VarType.VARTYPE_SHORT_OBJ);
  }
}
