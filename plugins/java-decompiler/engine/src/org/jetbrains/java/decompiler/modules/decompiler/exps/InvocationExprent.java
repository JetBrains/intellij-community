// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.ClasspathHelper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;
import org.jetbrains.java.decompiler.struct.consts.PooledConstant;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.struct.match.MatchNode.RuleValue;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.ListStack;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;

public class InvocationExprent extends Exprent {
  private static final int INVOKE_SPECIAL = 1;
  private static final int INVOKE_VIRTUAL = 2;
  private static final int INVOKE_STATIC = 3;
  private static final int INVOKE_INTERFACE = 4;
  public static final int INVOKE_DYNAMIC = 5;

  public static final int TYPE_GENERAL = 1;
  public static final int TYPE_INIT = 2;
  public static final int TYPE_CLINIT = 3;

  private static final BitSet EMPTY_BIT_SET = new BitSet(0);

  private String name;
  private String className;
  private boolean isStatic;
  private boolean canIgnoreBoxing = true;
  private int funcType = TYPE_GENERAL;
  private Exprent instance;
  private MethodDescriptor descriptor;
  private String stringDescriptor;
  private String invokeDynamicClassSuffix;
  private int invocationType = INVOKE_VIRTUAL;
  private List<Exprent> parameters = new ArrayList<>();
  private List<PooledConstant> bootstrapArguments;

  public InvocationExprent() {
    super(EXPRENT_INVOCATION);
  }

  public InvocationExprent(int opcode,
                           LinkConstant cn,
                           List<PooledConstant> bootstrapArguments,
                           ListStack<? extends Exprent> stack,
                           Set<Integer> bytecodeOffsets) {
    this();

    name = cn.elementname;
    className = cn.classname;
    this.bootstrapArguments = bootstrapArguments;
    switch (opcode) {
      case CodeConstants.opc_invokestatic -> invocationType = INVOKE_STATIC;
      case CodeConstants.opc_invokespecial -> invocationType = INVOKE_SPECIAL;
      case CodeConstants.opc_invokevirtual -> invocationType = INVOKE_VIRTUAL;
      case CodeConstants.opc_invokeinterface -> invocationType = INVOKE_INTERFACE;
      case CodeConstants.opc_invokedynamic -> {
        invocationType = INVOKE_DYNAMIC;

        className = "java/lang/Class"; // dummy class name
        invokeDynamicClassSuffix = "##Lambda_" + cn.index1 + "_" + cn.index2;
      }
    }

    if (CodeConstants.INIT_NAME.equals(name)) {
      funcType = TYPE_INIT;
    }
    else if (CodeConstants.CLINIT_NAME.equals(name)) {
      funcType = TYPE_CLINIT;
    }

    stringDescriptor = cn.descriptor;
    descriptor = MethodDescriptor.parseDescriptor(cn.descriptor);

    for (VarType ignored : descriptor.params) {
      parameters.add(0, stack.pop());
    }

    if (opcode == CodeConstants.opc_invokedynamic) {
      int dynamicInvocationType = -1;
      if (bootstrapArguments != null) {
        if (bootstrapArguments.size() > 1) { // INVOKEDYNAMIC is used not only for lambdas
          PooledConstant link = bootstrapArguments.get(1);
          if (link instanceof LinkConstant) {
            dynamicInvocationType = ((LinkConstant)link).index1;
          }
        }
      }
      if (dynamicInvocationType == CodeConstants.CONSTANT_MethodHandle_REF_invokeStatic) {
        isStatic = true;
      }
      else {
        // FIXME: remove the first parameter completely from the list. It's the object type for a virtual lambda method.
        if (!parameters.isEmpty()) {
          instance = parameters.get(0);
        }
      }
    }
    else if (opcode == CodeConstants.opc_invokestatic) {
      isStatic = true;
    }
    else {
      instance = stack.pop();
    }

    addBytecodeOffsets(bytecodeOffsets);
  }

  private InvocationExprent(InvocationExprent expr) {
    this();

    name = expr.getName();
    className = expr.getClassName();
    isStatic = expr.isStatic();
    canIgnoreBoxing = expr.canIgnoreBoxing;
    funcType = expr.getFuncType();
    instance = expr.getInstance();
    if (instance != null) {
      instance = instance.copy();
    }
    invocationType = expr.getInvocationType();
    invokeDynamicClassSuffix = expr.getInvokeDynamicClassSuffix();
    stringDescriptor = expr.getStringDescriptor();
    descriptor = expr.getDescriptor();

    List<Exprent> parameters = expr.getParameters();
    this.parameters = new ArrayList<>(parameters.size());
    for (Exprent parameter : parameters) this.parameters.add(parameter.copy());

    addBytecodeOffsets(expr.bytecode);
    bootstrapArguments = expr.getBootstrapArguments();
  }

  @Override
  public VarType getExprType() {
    return descriptor.ret;
  }

  @Override
  public CheckTypesResult checkExprTypeBounds() {
    CheckTypesResult result = new CheckTypesResult();

    for (int i = 0; i < parameters.size(); i++) {
      Exprent parameter = parameters.get(i);

      VarType leftType = descriptor.params[i];

      result.addMinTypeExprent(parameter, VarType.getMinTypeInFamily(leftType.getTypeFamily()));
      result.addMaxTypeExprent(parameter, leftType);
    }

    return result;
  }

  @Override
  public List<Exprent> getAllExprents() {
    List<Exprent> lst = new ArrayList<>();
    if (instance != null) {
      lst.add(instance);
    }
    lst.addAll(parameters);
    return lst;
  }


  @Override
  public Exprent copy() {
    return new InvocationExprent(this);
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    TextBuffer buf = new TextBuffer();

    String super_qualifier = null;
    boolean isInstanceThis = false;

    tracer.addMapping(bytecode);

    if (instance instanceof InvocationExprent) {
      ((InvocationExprent) instance).markUsingBoxingResult();
    }

    if (isStatic) {
      if (isBoxingCall() && canIgnoreBoxing) {
        // process general "boxing" calls, e.g. 'Object[] data = { true }' or 'Byte b = 123'
        // here 'byte' and 'short' values do not need an explicit narrowing type cast
        ExprProcessor.getCastedExprent(parameters.get(0), descriptor.params[0], buf, indent, false, false, false, false, tracer);
        return buf;
      }

      ClassNode node = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
      if (node == null || !className.equals(node.classStruct.qualifiedName)) {
        buf.append(DecompilerContext.getImportCollector().getNestedNameInClassContext(ExprProcessor.buildJavaClassName(className)));
      }
    }
    else {

      if (instance != null && instance.type == Exprent.EXPRENT_VAR) {
        VarExprent instVar = (VarExprent)instance;
        VarVersionPair varPair = new VarVersionPair(instVar);

        VarProcessor varProc = instVar.getProcessor();
        if (varProc == null) {
          MethodWrapper currentMethod = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
          if (currentMethod != null) {
            varProc = currentMethod.varproc;
          }
        }

        String this_classname = null;
        if (varProc != null) {
          this_classname = varProc.getThisVars().get(varPair);
        }

        if (this_classname != null) {
          isInstanceThis = true;

          if (invocationType == INVOKE_SPECIAL) {
            if (!className.equals(this_classname)) { // TODO: direct comparison to the super class?
              StructClass cl = DecompilerContext.getStructContext().getClass(className);
              boolean isInterface = cl != null && cl.hasModifier(CodeConstants.ACC_INTERFACE);
              super_qualifier = !isInterface ? this_classname : className;
            }
          }
        }
      }

      if (funcType == TYPE_GENERAL) {
        if (super_qualifier != null) {
          TextUtil.writeQualifiedSuper(buf, super_qualifier);
        }
        else if (instance != null) {
          TextBuffer res = instance.toJava(indent, tracer);

          if (isUnboxingCall()) {
            // we don't print the unboxing call - no need to bother with the instance wrapping / casting
            buf.append(res);
            return buf;
          }

          VarType rightType = instance.getExprType();
          VarType leftType = new VarType(CodeConstants.TYPE_OBJECT, 0, className);

          if (rightType.equals(VarType.VARTYPE_OBJECT) && !leftType.equals(rightType)) {
            buf.append("((").append(ExprProcessor.getCastTypeName(leftType, Collections.emptyList())).append(")");

            if (instance.getPrecedence() >= FunctionExprent.getPrecedence(FunctionExprent.FUNCTION_CAST)) {
              res.enclose("(", ")");
            }
            buf.append(res).append(")");
          }
          else if (instance.getPrecedence() > getPrecedence()) {
            buf.append("(").append(res).append(")");
          }
          else {
            buf.append(res);
          }
        }
      }
    }

    switch (funcType) {
      case TYPE_GENERAL -> {
        if (VarExprent.VAR_NAMELESS_ENCLOSURE.equals(buf.toString())) {
          buf = new TextBuffer();
        }

        if (buf.length() > 0) {
          buf.append(".");
        }

        buf.append(name);
        if (invocationType == INVOKE_DYNAMIC) {
          buf.append("<invokedynamic>");
        }
        buf.append("(");
      }
      case TYPE_CLINIT -> throw new RuntimeException("Explicit invocation of " + CodeConstants.CLINIT_NAME);
      case TYPE_INIT -> {
        if (super_qualifier != null) {
          buf.append("super(");
        }
        else if (isInstanceThis) {
          buf.append("this(");
        }
        else if (instance != null) {
          buf.append(instance.toJava(indent, tracer)).append(".<init>(");
        }
        else {
          throw new RuntimeException("Unrecognized invocation of " + CodeConstants.INIT_NAME);
        }
      }
    }

    List<VarVersionPair> mask = null;
    boolean isEnum = false;
    if (funcType == TYPE_INIT) {
      ClassNode newNode = DecompilerContext.getClassProcessor().getMapRootClasses().get(className);
      if (newNode != null) {
        mask = ExprUtil.getSyntheticParametersMask(newNode, stringDescriptor, parameters.size());
        isEnum = newNode.classStruct.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);
      }
    }

    BitSet setAmbiguousParameters = getAmbiguousParameters();

    // omit 'new Type[] {}' for the last parameter of a vararg method call
    if (parameters.size() == descriptor.params.length && isVarArgCall()) {
      Exprent lastParam = parameters.get(parameters.size() - 1);
      if (lastParam.type == EXPRENT_NEW && lastParam.getExprType().getArrayDim() >= 1) {
        ((NewExprent) lastParam).setVarArgParam(true);
      }
    }

    boolean firstParameter = true;
    int start = isEnum ? 2 : 0;
    for (int i = start; i < parameters.size(); i++) {
      if (mask == null || mask.get(i) == null) {
        TextBuffer buff = new TextBuffer();
        boolean ambiguous = setAmbiguousParameters.get(i);

        // 'byte' and 'short' literals need an explicit narrowing type cast when used as a parameter
        ExprProcessor.getCastedExprent(parameters.get(i), descriptor.params[i], buff, indent, true, ambiguous, true, true, tracer);

        // the last "new Object[0]" in the vararg call is not printed
        if (buff.length() > 0) {
          if (!firstParameter) {
            buf.append(", ");
          }
          buf.append(buff);
        }

        firstParameter = false;
      }
    }

    buf.append(')');

    return buf;
  }

  private boolean isVarArgCall() {
    StructClass cl = DecompilerContext.getStructContext().getClass(className);
    if (cl != null) {
      StructMethod mt = cl.getMethod(InterpreterUtil.makeUniqueKey(name, stringDescriptor));
      if (mt != null) {
        return mt.hasModifier(CodeConstants.ACC_VARARGS);
      }
    }
    else {
      // TODO: tap into IDEA indices to access libraries methods details

      // try to check the class on the classpath
      Method mtd = ClasspathHelper.findMethod(className, name, descriptor);
      return mtd != null && mtd.isVarArgs();
    }
    return false;
  }

  public boolean isBoxingCall() {
    if (isStatic && "valueOf".equals(name) && parameters.size() == 1) {
      int paramType = parameters.get(0).getExprType().getType();

      // special handling for ambiguous types
      if (parameters.get(0).type == Exprent.EXPRENT_CONST) {
        // 'Integer.valueOf(1)' has '1' type detected as TYPE_BYTECHAR
        // 'Integer.valueOf(40_000)' has '40_000' type detected as TYPE_CHAR
        // so we check the type family instead
        if (parameters.get(0).getExprType().getTypeFamily() == CodeConstants.TYPE_FAMILY_INTEGER) {
          if (className.equals("java/lang/Integer")) {
            return true;
          }
        }

        if (paramType == CodeConstants.TYPE_BYTECHAR || paramType == CodeConstants.TYPE_SHORTCHAR) {
          if (className.equals("java/lang/Character")) {
            return true;
          }
        }
      }

      return className.equals(getClassNameForPrimitiveType(paramType));
    }

    return false;
  }

  public boolean isInstanceCall(@NotNull String className, @NotNull String methodName, int parametersCount) {
    return invocationType == INVOKE_VIRTUAL &&
           this.className.equals(className) && methodName.equals(name) && parameters.size() == parametersCount;
  }

  public void markUsingBoxingResult() {
    canIgnoreBoxing = false;
  }

  // TODO: move to CodeConstants ???
  private static String getClassNameForPrimitiveType(int type) {
    return switch (type) {
      case CodeConstants.TYPE_BOOLEAN -> "java/lang/Boolean";
      case CodeConstants.TYPE_BYTE, CodeConstants.TYPE_BYTECHAR -> "java/lang/Byte";
      case CodeConstants.TYPE_CHAR -> "java/lang/Character";
      case CodeConstants.TYPE_SHORT, CodeConstants.TYPE_SHORTCHAR -> "java/lang/Short";
      case CodeConstants.TYPE_INT -> "java/lang/Integer";
      case CodeConstants.TYPE_LONG -> "java/lang/Long";
      case CodeConstants.TYPE_FLOAT -> "java/lang/Float";
      case CodeConstants.TYPE_DOUBLE -> "java/lang/Double";
      default -> null;
    };
  }

  private static final Map<String, String> UNBOXING_METHODS = Map.of(
    "booleanValue", "java/lang/Boolean",
    "byteValue", "java/lang/Byte",
    "shortValue", "java/lang/Short",
    "intValue", "java/lang/Integer",
    "longValue", "java/lang/Long",
    "floatValue", "java/lang/Float",
    "doubleValue", "java/lang/Double",
    "charValue", "java/lang/Character"
  );

  private boolean isUnboxingCall() {
    return !isStatic && parameters.size() == 0 && className.equals(UNBOXING_METHODS.get(name));
  }

  private BitSet getAmbiguousParameters() {
    StructClass cl = DecompilerContext.getStructContext().getClass(className);
    if (cl == null) return EMPTY_BIT_SET;

    // check number of matches
    List<MethodDescriptor> matches = new ArrayList<>();
    nextMethod:
    for (StructMethod mt : cl.getMethods()) {
      if (name.equals(mt.getName())) {
        MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());
        if (md.params.length == descriptor.params.length) {
          for (int i = 0; i < md.params.length; i++) {
            if (md.params[i].getTypeFamily() != descriptor.params[i].getTypeFamily()) {
              continue nextMethod;
            }
          }
          matches.add(md);
        }
      }
    }
    if (matches.size() == 1) return EMPTY_BIT_SET;

    // check if a call is unambiguous
    StructMethod mt = cl.getMethod(InterpreterUtil.makeUniqueKey(name, stringDescriptor));
    if (mt != null) {
      MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());
      if (md.params.length == parameters.size()) {
        boolean exact = true;
        for (int i = 0; i < md.params.length; i++) {
          if (!md.params[i].equals(parameters.get(i).getExprType())) {
            exact = false;
            break;
          }
        }
        if (exact) return EMPTY_BIT_SET;
      }
    }

    // mark parameters
    BitSet ambiguous = new BitSet(descriptor.params.length);
    for (int i = 0; i < descriptor.params.length; i++) {
      VarType paramType = descriptor.params[i];
      for (MethodDescriptor md : matches) {
        if (!paramType.equals(md.params[i])) {
          ambiguous.set(i);
          break;
        }
      }
    }
    return ambiguous;
  }

  @Override
  public void replaceExprent(Exprent oldExpr, Exprent newExpr) {
    if (oldExpr == instance) {
      instance = newExpr;
    }

    for (int i = 0; i < parameters.size(); i++) {
      if (oldExpr == parameters.get(i)) {
        parameters.set(i, newExpr);
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof InvocationExprent it)) return false;

    return Objects.equals(name, it.name) &&
           Objects.equals(className, it.className) &&
           isStatic == it.isStatic &&
           Objects.equals(instance, it.instance) &&
           Objects.equals(descriptor, it.descriptor) &&
           funcType == it.funcType &&
           Objects.equals(parameters, it.parameters);
  }

  public List<Exprent> getParameters() {
    return parameters;
  }

  public void setParameters(List<Exprent> parameters) {
    this.parameters = parameters;
  }

  public MethodDescriptor getDescriptor() {
    return descriptor;
  }

  public void setDescriptor(MethodDescriptor descriptor) {
    this.descriptor = descriptor;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public int getFuncType() {
    return funcType;
  }

  public void setFuncType(int funcType) {
    this.funcType = funcType;
  }

  public Exprent getInstance() {
    return instance;
  }

  public void setInstance(Exprent instance) {
    this.instance = instance;
  }

  public boolean isStatic() {
    return isStatic;
  }

  public void setStatic(boolean isStatic) {
    this.isStatic = isStatic;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getStringDescriptor() {
    return stringDescriptor;
  }

  public void setStringDescriptor(String stringDescriptor) {
    this.stringDescriptor = stringDescriptor;
  }

  public int getInvocationType() {
    return invocationType;
  }

  public String getInvokeDynamicClassSuffix() {
    return invokeDynamicClassSuffix;
  }

  public List<PooledConstant> getBootstrapArguments() {
    return bootstrapArguments;
  }

  // *****************************************************************************
  // IMatchable implementation
  // *****************************************************************************

  @Override
  public boolean match(MatchNode matchNode, MatchEngine engine) {
    if (!super.match(matchNode, engine)) {
      return false;
    }

    for (Entry<MatchProperties, RuleValue> rule : matchNode.getRules().entrySet()) {
      RuleValue value = rule.getValue();

      MatchProperties key = rule.getKey();
      if (key == MatchProperties.EXPRENT_INVOCATION_PARAMETER) {
        if (value.isVariable() && (value.parameter >= parameters.size() ||
                                   !engine.checkAndSetVariableValue(value.value.toString(), parameters.get(value.parameter)))) {
          return false;
        }
      }
      else if (key == MatchProperties.EXPRENT_INVOCATION_CLASS) {
        if (!value.value.equals(this.className)) {
          return false;
        }
      }
      else if (key == MatchProperties.EXPRENT_INVOCATION_SIGNATURE) {
        if (!value.value.equals(this.name + this.stringDescriptor)) {
          return false;
        }
      }
    }

    return true;
  }
}