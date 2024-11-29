// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersion;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericClassDescriptor;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericMethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericType;
import org.jetbrains.java.decompiler.struct.match.IMatchable;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.struct.match.MatchNode.RuleValue;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.*;
import java.util.Map.Entry;

public abstract class Exprent implements IMatchable {
  public static final int MULTIPLE_USES = 1;
  public static final int SIDE_EFFECTS_FREE = 2;
  public static final int BOTH_FLAGS = 3;

  public static final int EXPRENT_ARRAY = 1;
  public static final int EXPRENT_ASSIGNMENT = 2;
  public static final int EXPRENT_CONST = 3;
  public static final int EXPRENT_EXIT = 4;
  public static final int EXPRENT_FIELD = 5;
  public static final int EXPRENT_FUNCTION = 6;
  public static final int EXPRENT_IF = 7;
  public static final int EXPRENT_INVOCATION = 8;
  public static final int EXPRENT_MONITOR = 9;
  public static final int EXPRENT_NEW = 10;
  public static final int EXPRENT_SWITCH = 11;
  public static final int EXPRENT_VAR = 12;
  public static final int EXPRENT_ANNOTATION = 13;
  public static final int EXPRENT_ASSERT = 14;

  public final int type;
  public final int id;
  @Nullable
  public BitSet bytecode = null;  // offsets of bytecode instructions decompiled to this exprent

  public Exprent(int type) {
    this.type = type;
    this.id = DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.EXPRESSION_COUNTER);
  }

  public int getPrecedence() {
    return 0; // the highest precedence
  }

  @NotNull
  public VarType getExprType() {
    return VarType.VARTYPE_VOID;
  }


  /**
   * Infers and sets the expression type with the given upper bound.
   *
   * @param upperBound The upper bound type to be used for inferring the expression type.
   */
  public void inferExprType(VarType upperBound) {
  }

  public int getExprentUse() {
    return 0;
  }

  public CheckTypesResult checkExprTypeBounds() {
    return null;
  }

  public boolean containsExprent(Exprent exprent) {
    if (equals(exprent)) {
      return true;
    }
    List<Exprent> lst = getAllExprents();
    for (int i = lst.size() - 1; i >= 0; i--) {
      if (lst.get(i).containsExprent(exprent)) {
        return true;
      }
    }
    return false;
  }

  public final List<Exprent> getAllExprents(boolean recursive) {
    List<Exprent> lst = new ArrayList<>();
    getAllExprents(recursive, lst);

    return lst;
  }

  private List<Exprent> getAllExprents(boolean recursive, List<Exprent> list) {
    int start = list.size();
    getAllExprents(list);
    int end = list.size();

    if (recursive) {
      for (int i = end - 1; i >= start; i--) {
        list.get(i).getAllExprents(true, list);
      }
    }

    return list;
  }

  public Set<VarVersion> getAllVariables() {
    List<Exprent> lstAllExprents = getAllExprents(true);
    lstAllExprents.add(this);

    Set<VarVersion> set = new HashSet<>();
    for (Exprent expr : lstAllExprents) {
      if (expr.type == EXPRENT_VAR) {
        set.add(new VarVersion((VarExprent)expr));
      }
    }
    return set;
  }

  public final List<Exprent> getAllExprents() {
    List<Exprent> list = new ArrayList<>();
    getAllExprents(list);

    return list;
  }

  // Get all the exprents contained within the current one
  // Preconditions: this list must never be removed from! Only added to!
  protected abstract List<Exprent> getAllExprents(List<Exprent> list);

  public Exprent copy() {
    throw new RuntimeException("not implemented");
  }

  public TextBuffer toJava() {
    return toJava(0, BytecodeMappingTracer.DUMMY);
  }

  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    throw new RuntimeException("not implemented");
  }

  public void replaceExprent(Exprent oldExpr, Exprent newExpr) { }

  public void addBytecodeOffsets(@Nullable BitSet bytecodeOffsets) {
    if (bytecodeOffsets != null) {
      if (bytecode == null) {
        bytecode = new BitSet();
      }
      bytecode.or(bytecodeOffsets);
    }
  }

  /**
   * Fills the given BitSet with bytecode range data.
   * This bytecode data represents bytecode offset which is used to map with source lines
   */
  public abstract void fillBytecodeRange(@Nullable BitSet values);

  protected void measureBytecode(@Nullable BitSet values) {
    if (bytecode != null && values != null)
      values.or(bytecode);
  }

  protected static void measureBytecode(@Nullable BitSet values, @Nullable Exprent exprent) {
    if (exprent != null)
      exprent.fillBytecodeRange(values);
  }

  protected static void measureBytecode(@Nullable BitSet values, @Nullable List<? extends Exprent> list) {
    if (list != null && !list.isEmpty()) {
      for (Exprent e : list)
        e.fillBytecodeRange(values);
    }
  }

  public static List<? extends Exprent> sortIndexed(List<? extends Exprent> lst) {
      List<Exprent> ret = new ArrayList<>();
      List<VarExprent> defs = new ArrayList<>();

      Comparator<VarExprent> comp = Comparator.comparingInt(VarExprent::getIndex);

      for (Exprent exp : lst) {
        boolean isDef = exp instanceof VarExprent && ((VarExprent)exp).isDefinition();
        if (!isDef) {
          if (!defs.isEmpty()) {
            Collections.sort(defs, comp);
            ret.addAll(defs);
            defs.clear();
          }
          ret.add(exp);
        }
        else {
          defs.add((VarExprent)exp);
        }
      }

      if (!defs.isEmpty()) {
        Collections.sort(defs, comp);
        ret.addAll(defs);
      }
      return ret;
    }

  protected VarType gatherGenerics(VarType upperBound, VarType ret, List<String> fparams, List<VarType> genericArgs) {
    Map<VarType, VarType> map = new HashMap<>();

    // List<T> -> List<String>
    //for example:
    //Bar<String, String> bar = new Bar<String, String>();
    //it replace Bar<T, L> to Bar<String, String>
    if (upperBound != null && upperBound.isGeneric() && ret.isGeneric()) {
      List<VarType> leftArgs = ((GenericType)upperBound).getArguments();
      List<VarType> rightArgs = ((GenericType)ret).getArguments();
      if (leftArgs.size() == rightArgs.size() && rightArgs.size() == fparams.size()) {
        for (int i = 0; i < leftArgs.size(); i++) {
          VarType left = leftArgs.get(i);
          VarType right = rightArgs.get(i);
          if (left != null && right.getValue().equals(fparams.get(i))) {
            genericArgs.add(left);
            map.put(right, left);
          } else {
            genericArgs.clear();
            map.clear();
            break;
          }
        }
      }
    }

    return map.isEmpty() ? ret : ret.remap(map);
  }

  protected void appendParameters(TextBuffer buf, List<VarType> genericArgs) {
    if (genericArgs.isEmpty()) {
      return;
    }
    buf.append("<");
    //TODO: Check target output level and use <> operator?
    for (int i = 0; i < genericArgs.size(); i++) {
      buf.append(ExprProcessor.getCastTypeName(genericArgs.get(i), Collections.emptyList()));
      if(i + 1 < genericArgs.size()) {
        buf.append(", ");
      }
    }
    buf.append(">");
  }

  /**
   * Example:
   * <pre>
   * {@code
   *  class A <A, B extends TestGenerics.Maps & List>{
   *
   *  }
   * }
   * </pre>
   * for this case, the result will be
   *  <pre>
   *  'A' -> Object
   *  'B' - [TestGenerics.Maps, List]
   *  </pre>
   */
  protected Map<VarType, List<VarType>> getNamedGenerics() {
    Map<VarType, List<VarType>> ret = new HashMap<>();
    ClassNode class_ = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
    MethodWrapper method = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);

    //TODO: Loop enclosing classes?
    GenericClassDescriptor cls = class_ == null ? null : class_.classStruct.getSignature();
    if (cls != null) {
      for (int x = 0; x < cls.fparameters.size(); x++) {
        ret.put(GenericType.parse("T" + cls.fparameters.get(x) + ";"), cls.fbounds.get(x));
      }
    }

    //TODO: Loop enclosing method?
    GenericMethodDescriptor mtd = method == null ? null : method.methodStruct.getSignature();
    if (mtd != null) {
      for (int x = 0; x < mtd.typeParameters.size(); x++) {
        ret.put(GenericType.parse("T" + mtd.typeParameters.get(x) + ";"), mtd.typeParameterBounds.get(x));
      }
    }

    return ret;
  }

  protected void wrapInCast(VarType left, VarType right, TextBuffer buf, int precedence) {
    boolean needsCast =
      (left != null && !left.isSuperset(right)) && (right.equals(VarType.VARTYPE_OBJECT) || left.getType() != CodeConstants.TYPE_OBJECT);

    if (left != null && right != null && (left.isGeneric() || right.isGeneric())) {
      Map<VarType, List<VarType>> names = this.getNamedGenerics();
      int arrayDim = 0;

      if (left.getArrayDim() == right.getArrayDim() && left.getArrayDim() > 0) {
        arrayDim = left.getArrayDim();
        left = left.resizeArrayDim(0);
        right = right.resizeArrayDim(0);
      }

      List<? extends VarType> types = names.get(right);
      if (types == null) {
        types = names.get(left);
      }

      if (types != null) {
        boolean anyMatch = false; //TODO: allMatch instead of anyMatch?
        for (VarType type : types) {
          if (type.equals(VarType.VARTYPE_OBJECT) && right.equals(VarType.VARTYPE_OBJECT)) {
            continue;
          }
          anyMatch = right.getValue() == null /*null const doesn't need cast*/ ||
                     DecompilerContext.getStructContext().instanceOf(right.getValue(), type.getValue());
          if (anyMatch) {
            break;
          }
        }

        if (anyMatch) {
          needsCast = false;
        }
      }

      if (arrayDim != 0) {
        left = left.resizeArrayDim(arrayDim);
      }
    }

    if (!needsCast) {
      return;
    }

    if (precedence >= FunctionExprent.getPrecedence(FunctionExprent.FUNCTION_CAST)) {
      buf.enclose("(", ")");
    }

    buf.prepend("(" + ExprProcessor.getCastTypeName(left, Collections.emptyList()) + ")");
  }

  // *****************************************************************************
  // IMatchable implementation
  // *****************************************************************************

  @Override
  public IMatchable findObject(MatchNode matchNode, int index) {
    if (matchNode.getType() != MatchNode.MATCHNODE_EXPRENT) {
      return null;
    }

    List<Exprent> lstAllExprents = getAllExprents();
    if (lstAllExprents.isEmpty()) {
      return null;
    }

    String position = (String)matchNode.getRuleValue(MatchProperties.EXPRENT_POSITION);
    if (position != null) {
      if (position.matches("-?\\d+")) {
        return lstAllExprents.get((lstAllExprents.size() + Integer.parseInt(position)) % lstAllExprents.size()); // care for negative positions
      }
    }
    else if (index < lstAllExprents.size()) { // use 'index' parameter
      return lstAllExprents.get(index);
    }

    return null;
  }

  @Override
  public boolean match(MatchNode matchNode, MatchEngine engine) {
    if (matchNode.getType() != MatchNode.MATCHNODE_EXPRENT) {
      return false;
    }

    for (Entry<MatchProperties, RuleValue> rule : matchNode.getRules().entrySet()) {
      MatchProperties key = rule.getKey();
      if (key == MatchProperties.EXPRENT_TYPE && this.type != (Integer)rule.getValue().value) {
        return false;
      }
      if (key == MatchProperties.EXPRENT_RET && !engine.checkAndSetVariableValue((String)rule.getValue().value, this)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public String toString() {
    return toJava(0, BytecodeMappingTracer.DUMMY).toString();
  }
}