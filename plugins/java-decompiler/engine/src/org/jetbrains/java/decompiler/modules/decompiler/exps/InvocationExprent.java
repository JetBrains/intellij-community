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
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPaar;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.ListStack;

import java.util.*;


public class InvocationExprent extends Exprent {

  public static final int INVOKE_SPECIAL = 1;
  public static final int INVOKE_VIRTUAL = 2;
  public static final int INVOKE_STATIC = 3;
  public static final int INVOKE_INTERFACE = 4;
  public static final int INVOKE_DYNAMIC = 5;

  public static final int TYP_GENERAL = 1;
  public static final int TYP_INIT = 2;
  public static final int TYP_CLINIT = 3;

  public static final int CONSTRUCTOR_NOT = 0;
  public static final int CONSTRUCTOR_THIS = 1;
  public static final int CONSTRUCTOR_SUPER = 2;

  private String name;

  private String classname;

  private boolean isStatic;

  private int functype = TYP_GENERAL;

  private Exprent instance;

  private MethodDescriptor descriptor;

  private String stringDescriptor;

  private String invoke_dynamic_classsuffix;

  private int invocationTyp = INVOKE_VIRTUAL;

  private List<Exprent> lstParameters = new ArrayList<Exprent>();

  {
    this.type = EXPRENT_INVOCATION;
  }

  public InvocationExprent() {
  }

  public InvocationExprent(int opcode, LinkConstant cn, ListStack<Exprent> stack, int dynamic_invokation_type) {

    name = cn.elementname;
    classname = cn.classname;

    switch (opcode) {
      case CodeConstants.opc_invokestatic:
        invocationTyp = INVOKE_STATIC;
        break;
      case CodeConstants.opc_invokespecial:
        invocationTyp = INVOKE_SPECIAL;
        break;
      case CodeConstants.opc_invokevirtual:
        invocationTyp = INVOKE_VIRTUAL;
        break;
      case CodeConstants.opc_invokeinterface:
        invocationTyp = INVOKE_INTERFACE;
        break;
      case CodeConstants.opc_invokedynamic:
        invocationTyp = INVOKE_DYNAMIC;

        classname = "java/lang/Class"; // dummy class name
        invoke_dynamic_classsuffix = "##Lambda_" + cn.index1 + "_" + cn.index2;
    }

    if ("<init>".equals(name)) {
      functype = TYP_INIT;
    }
    else if ("<clinit>".equals(name)) {
      functype = TYP_CLINIT;
    }

    stringDescriptor = cn.descriptor;
    descriptor = MethodDescriptor.parseDescriptor(cn.descriptor);

    for (int i = 0; i < descriptor.params.length; i++) {
      lstParameters.add(0, stack.pop());
    }

    if (opcode == CodeConstants.opc_invokedynamic) {
      if (dynamic_invokation_type == CodeConstants.CONSTANT_MethodHandle_REF_invokeStatic) {
        isStatic = true;
      }
      else {
        instance = lstParameters
          .get(0); // FIXME: remove the first parameter completely from the list. It's the object type for a virtual lambda method.
      }
    }
    else if (opcode == CodeConstants.opc_invokestatic) {
      isStatic = true;
    }
    else {
      instance = stack.pop();
    }
  }

  private InvocationExprent(InvocationExprent expr) {
    name = expr.getName();
    classname = expr.getClassname();
    isStatic = expr.isStatic();
    functype = expr.getFunctype();
    instance = expr.getInstance();
    if (instance != null) {
      instance = instance.copy();
    }
    invocationTyp = expr.getInvocationTyp();
    stringDescriptor = expr.getStringDescriptor();
    descriptor = expr.getDescriptor();
    lstParameters = new ArrayList<Exprent>(expr.getLstParameters());
    for (int i = 0; i < lstParameters.size(); i++) {
      lstParameters.set(i, lstParameters.get(i).copy());
    }
  }


  public VarType getExprType() {
    return descriptor.ret;
  }

  public CheckTypesResult checkExprTypeBounds() {
    CheckTypesResult result = new CheckTypesResult();

    for (int i = 0; i < lstParameters.size(); i++) {
      Exprent parameter = lstParameters.get(i);

      VarType leftType = descriptor.params[i];

      result.addMinTypeExprent(parameter, VarType.getMinTypeInFamily(leftType.type_family));
      result.addMaxTypeExprent(parameter, leftType);
    }

    return result;
  }

  public List<Exprent> getAllExprents() {
    List<Exprent> lst = new ArrayList<Exprent>();
    if (instance != null) {
      lst.add(instance);
    }
    lst.addAll(lstParameters);
    return lst;
  }


  public Exprent copy() {
    return new InvocationExprent(this);
  }

  public String toJava(int indent) {
    StringBuilder buf = new StringBuilder("");

    String super_qualifier = null;
    boolean isInstanceThis = false;

    if (invocationTyp == INVOKE_DYNAMIC) {
      //			ClassNode node = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASSNODE);
      //
      //			if(node != null) {
      //				ClassNode lambda_node = DecompilerContext.getClassprocessor().getMapRootClasses().get(node.classStruct.qualifiedName + invoke_dynamic_classsuffix);
      //				if(lambda_node != null) {
      //
      //					String typename = ExprProcessor.getCastTypeName(lambda_node.anonimousClassType);
      //
      //					StringWriter strwriter = new StringWriter();
      //					BufferedWriter bufstrwriter = new BufferedWriter(strwriter);
      //
      //					ClassWriter clwriter = new ClassWriter();
      //
      //					try {
      //						bufstrwriter.write("new " + typename + "() {");
      //						bufstrwriter.newLine();
      //
      //
      //
      //						bufstrwriter.flush();
      //					} catch(IOException ex) {
      //						throw new RuntimeException(ex);
      //					}
      //
      //					buf.append(strwriter.toString());
      //
      //				}
      //			}

    }
    else if (isStatic) {

      ClassNode node = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
      if (node == null || !classname.equals(node.classStruct.qualifiedName)) {
        buf.append(DecompilerContext.getImportCollector().getShortName(ExprProcessor.buildJavaClassName(classname)));
      }
    }
    else {

      if (instance != null && instance.type == Exprent.EXPRENT_VAR) {
        VarExprent instvar = (VarExprent)instance;
        VarVersionPaar varpaar = new VarVersionPaar(instvar);

        VarProcessor vproc = instvar.getProcessor();
        if (vproc == null) {
          MethodWrapper current_meth = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
          if (current_meth != null) {
            vproc = current_meth.varproc;
          }
        }

        String this_classname = null;
        if (vproc != null) {
          this_classname = vproc.getThisvars().get(varpaar);
        }

        if (this_classname != null) {
          isInstanceThis = true;

          if (invocationTyp == INVOKE_SPECIAL) {
            if (!classname.equals(this_classname)) { // TODO: direct comparison to the super class?
              super_qualifier = this_classname;
            }
          }
        }
      }

      if (functype == TYP_GENERAL) {
        if (super_qualifier != null) {
          StructClass current_class = ((ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE)).classStruct;

          if (!super_qualifier.equals(current_class.qualifiedName)) {
            buf.append(DecompilerContext.getImportCollector().getShortName(ExprProcessor.buildJavaClassName(super_qualifier)));
            buf.append(".");
          }
          buf.append("super");
        }
        else {
          String res = instance.toJava(indent);

          VarType rightType = instance.getExprType();
          VarType leftType = new VarType(CodeConstants.TYPE_OBJECT, 0, classname);

          if (rightType.equals(VarType.VARTYPE_OBJECT) && !leftType.equals(rightType)) {
            buf.append("((").append(ExprProcessor.getCastTypeName(leftType)).append(")");

            if (instance.getPrecedence() >= FunctionExprent.getPrecedence(FunctionExprent.FUNCTION_CAST)) {
              res = "(" + res + ")";
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

    switch (functype) {
      case TYP_GENERAL:
        if (VarExprent.VAR_NAMELESS_ENCLOSURE.equals(buf.toString())) {
          buf = new StringBuilder("");
        }

        if (buf.length() > 0) {
          buf.append(".");
        }

        buf.append(name);
        if (invocationTyp == INVOKE_DYNAMIC) {
          buf.append("<invokedynamic>");
        }
        buf.append("(");

        break;
      case TYP_CLINIT:
        throw new RuntimeException("Explicite invocation of <clinit>");
      case TYP_INIT:
        if (super_qualifier != null) {
          buf.append("super(");
        }
        else if (isInstanceThis) {
          buf.append("this(");
        }
        else {
          buf.append(instance.toJava(indent));
          buf.append(".<init>(");
          //				throw new RuntimeException("Unrecognized invocation of <init>"); // FIXME: activate
        }
    }

    List<VarVersionPaar> sigFields = null;
    boolean isEnum = false;
    if (functype == TYP_INIT) {
      ClassNode newNode = DecompilerContext.getClassProcessor().getMapRootClasses().get(classname);

      if (newNode != null) {  // own class
        if (newNode.wrapper != null) {
          sigFields = newNode.wrapper.getMethodWrapper("<init>", stringDescriptor).signatureFields;
        }
        else {
          if (newNode.type == ClassNode.CLASS_MEMBER && (newNode.access & CodeConstants.ACC_STATIC) == 0) { // non-static member class
            sigFields = new ArrayList<VarVersionPaar>(Collections.nCopies(lstParameters.size(), (VarVersionPaar)null));
            sigFields.set(0, new VarVersionPaar(-1, 0));
          }
        }
        isEnum = newNode.classStruct.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);
      }
    }

    Set<Integer> setAmbiguousParameters = getAmbiguousParameters();

    boolean firstpar = true;
    int start = isEnum ? 2 : 0;
    for (int i = start; i < lstParameters.size(); i++) {
      if (sigFields == null || sigFields.get(i) == null) {
        if (!firstpar) {
          buf.append(", ");
        }

        StringBuilder buff = new StringBuilder();
        ExprProcessor.getCastedExprent(lstParameters.get(i), descriptor.params[i], buff, indent, true, setAmbiguousParameters.contains(i));

        buf.append(buff);
        firstpar = false;
      }
    }
    buf.append(")");

    return buf.toString();
  }

  private Set<Integer> getAmbiguousParameters() {

    Set<Integer> ret = new HashSet<Integer>();

    StructClass cstr = DecompilerContext.getStructContext().getClass(classname);
    if (cstr != null) {
      List<MethodDescriptor> lstMethods = new ArrayList<MethodDescriptor>();
      for (StructMethod meth : cstr.getMethods()) {
        if (name.equals(meth.getName())) {
          MethodDescriptor md = MethodDescriptor.parseDescriptor(meth.getDescriptor());
          if (md.params.length == descriptor.params.length) {
            boolean equals = true;
            for (int i = 0; i < md.params.length; i++) {
              if (md.params[i].type_family != descriptor.params[i].type_family) {
                equals = false;
                break;
              }
            }

            if (equals) {
              lstMethods.add(md);
            }
          }
        }
      }

      if (lstMethods.size() > 1) {
        for (int i = 0; i < descriptor.params.length; i++) {
          VarType partype = descriptor.params[i];

          for (MethodDescriptor md : lstMethods) {
            if (!partype.equals(md.params[i])) {
              ret.add(i);
              break;
            }
          }
        }
      }
    }

    return ret;
  }

  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof InvocationExprent)) return false;

    InvocationExprent it = (InvocationExprent)o;
    return InterpreterUtil.equalObjects(name, it.getName()) &&
           InterpreterUtil.equalObjects(classname, it.getClassname()) &&
           isStatic == it.isStatic() &&
           InterpreterUtil.equalObjects(instance, it.getInstance()) &&
           InterpreterUtil.equalObjects(descriptor, it.getDescriptor()) &&
           functype == it.getFunctype() &&
           InterpreterUtil.equalLists(lstParameters, it.getLstParameters());
  }

  public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
    if (oldexpr == instance) {
      instance = newexpr;
    }

    for (int i = 0; i < lstParameters.size(); i++) {
      if (oldexpr == lstParameters.get(i)) {
        lstParameters.set(i, newexpr);
      }
    }
  }

  public List<Exprent> getLstParameters() {
    return lstParameters;
  }

  public void setLstParameters(List<Exprent> lstParameters) {
    this.lstParameters = lstParameters;
  }

  public MethodDescriptor getDescriptor() {
    return descriptor;
  }

  public void setDescriptor(MethodDescriptor descriptor) {
    this.descriptor = descriptor;
  }

  public String getClassname() {
    return classname;
  }

  public void setClassname(String classname) {
    this.classname = classname;
  }

  public int getFunctype() {
    return functype;
  }

  public void setFunctype(int functype) {
    this.functype = functype;
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

  public int getInvocationTyp() {
    return invocationTyp;
  }

  public void setInvocationTyp(int invocationTyp) {
    this.invocationTyp = invocationTyp;
  }

  public String getInvokeDynamicClassSuffix() {
    return invoke_dynamic_classsuffix;
  }
}
