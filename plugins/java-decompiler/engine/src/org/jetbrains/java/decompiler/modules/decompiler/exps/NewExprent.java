/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.java.decompiler.main.ClassWriter;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.ListStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class NewExprent extends Exprent {
  private InvocationExprent constructor;
  private final VarType newType;
  private List<Exprent> lstDims = new ArrayList<>();
  private List<Exprent> lstArrayElements = new ArrayList<>();
  private boolean directArrayInit;
  private boolean anonymous;
  private boolean lambda;
  private boolean enumConst;

  public NewExprent(VarType newType, ListStack<Exprent> stack, int arrayDim, Set<Integer> bytecodeOffsets) {
    this(newType, getDimensions(arrayDim, stack), bytecodeOffsets);
  }

  public NewExprent(VarType newType, List<Exprent> lstDims, Set<Integer> bytecodeOffsets) {
    super(EXPRENT_NEW);
    this.newType = newType;
    this.lstDims = lstDims;

    anonymous = false;
    lambda = false;
    if (newType.type == CodeConstants.TYPE_OBJECT && newType.arrayDim == 0) {
      ClassNode node = DecompilerContext.getClassProcessor().getMapRootClasses().get(newType.value);
      if (node != null && (node.type == ClassNode.CLASS_ANONYMOUS || node.type == ClassNode.CLASS_LAMBDA)) {
        anonymous = true;
        if (node.type == ClassNode.CLASS_LAMBDA) {
          lambda = true;
        }
      }
    }

    addBytecodeOffsets(bytecodeOffsets);
  }

  private static List<Exprent> getDimensions(int arrayDim, ListStack<Exprent> stack) {
    List<Exprent> lstDims = new ArrayList<>();
    for (int i = 0; i < arrayDim; i++) {
      lstDims.add(0, stack.pop());
    }
    return lstDims;
  }

  @Override
  public VarType getExprType() {
    return anonymous ? DecompilerContext.getClassProcessor().getMapRootClasses().get(newType.value).anonymousClassType : newType;
  }

  @Override
  public CheckTypesResult checkExprTypeBounds() {
    CheckTypesResult result = new CheckTypesResult();

    if (newType.arrayDim != 0) {
      for (Exprent dim : lstDims) {
        result.addMinTypeExprent(dim, VarType.VARTYPE_BYTECHAR);
        result.addMaxTypeExprent(dim, VarType.VARTYPE_INT);
      }

      if (newType.arrayDim == 1) {
        VarType leftType = newType.decreaseArrayDim();
        for (Exprent element : lstArrayElements) {
          result.addMinTypeExprent(element, VarType.getMinTypeInFamily(leftType.typeFamily));
          result.addMaxTypeExprent(element, leftType);
        }
      }
    }
    else {
      if (constructor != null) {
        return constructor.checkExprTypeBounds();
      }
    }

    return result;
  }

  @Override
  public List<Exprent> getAllExprents() {
    List<Exprent> lst = new ArrayList<>();
    if (newType.arrayDim == 0) {
      if (constructor != null) {
        Exprent constructor_instance = constructor.getInstance();

        if (constructor_instance != null) { // should be true only for a lambda expression with a virtual content method
          lst.add(constructor_instance);
        }

        lst.addAll(constructor.getLstParameters());
      }
    }
    else {
      lst.addAll(lstDims);
      lst.addAll(lstArrayElements);
    }

    return lst;
  }

  @Override
  public Exprent copy() {
    List<Exprent> lst = new ArrayList<>();
    for (Exprent expr : lstDims) {
      lst.add(expr.copy());
    }

    NewExprent ret = new NewExprent(newType, lst, bytecode);
    ret.setConstructor(constructor == null ? null : (InvocationExprent)constructor.copy());
    ret.setLstArrayElements(lstArrayElements);
    ret.setDirectArrayInit(directArrayInit);
    ret.setAnonymous(anonymous);
    ret.setEnumConst(enumConst);
    return ret;
  }

  @Override
  public int getPrecedence() {
    return 1; // precedence of new
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    TextBuffer buf = new TextBuffer();

    if (anonymous) {
      ClassNode child = DecompilerContext.getClassProcessor().getMapRootClasses().get(newType.value);

      if (!enumConst) {
        String enclosing = null;

        if (!lambda && constructor != null) {
          enclosing = getQualifiedNewInstance(child.anonymousClassType.value, constructor.getLstParameters(), indent, tracer);
          if (enclosing != null) {
            buf.append(enclosing).append('.');
          }
        }

        buf.append("new ");

        String typename = ExprProcessor.getCastTypeName(child.anonymousClassType);
        if (enclosing != null) {
          ClassNode anonymousNode = DecompilerContext.getClassProcessor().getMapRootClasses().get(child.anonymousClassType.value);
          if (anonymousNode != null) {
            typename = anonymousNode.simpleName;
          }
          else {
            typename = typename.substring(typename.lastIndexOf('.') + 1);
          }
        }
        buf.append(typename);
      }

      buf.append('(');

      if (!lambda && constructor != null) {
        InvocationExprent invSuper = child.superInvocation;

        ClassNode newNode = DecompilerContext.getClassProcessor().getMapRootClasses().get(invSuper.getClassname());

        List<VarVersionPair> sigFields = null;
        if (newNode != null) { // own class
          if (newNode.getWrapper() != null) {
            sigFields = newNode.getWrapper().getMethodWrapper(CodeConstants.INIT_NAME, invSuper.getStringDescriptor()).signatureFields;
          }
          else {
            if (newNode.type == ClassNode.CLASS_MEMBER && (newNode.access & CodeConstants.ACC_STATIC) == 0 &&
                !constructor.getLstParameters().isEmpty()) { // member non-static class invoked with enclosing class instance
              sigFields = new ArrayList<>(Collections.nCopies(constructor.getLstParameters().size(), (VarVersionPair)null));
              sigFields.set(0, new VarVersionPair(-1, 0));
            }
          }
        }

        boolean firstParam = true;
        int start = 0, end = invSuper.getLstParameters().size();
        if (enumConst) {
          start += 2;
          end -= 1;
        }
        for (int i = start; i < end; i++) {
          if (sigFields == null || sigFields.get(i) == null) {
            if (!firstParam) {
              buf.append(", ");
            }

            Exprent param = invSuper.getLstParameters().get(i);
            if (param.type == Exprent.EXPRENT_VAR) {
              int varIndex = ((VarExprent)param).getIndex();
              if (varIndex > 0 && varIndex <= constructor.getLstParameters().size()) {
                param = constructor.getLstParameters().get(varIndex - 1);
              }
            }

            ExprProcessor.getCastedExprent(param, invSuper.getDescriptor().params[i], buf, indent, true, tracer);

            firstParam = false;
          }
        }
      }

      buf.append(')');

      if (enumConst && buf.length() == 2) {
        buf.setLength(0);
      }

      if (lambda) {
        if (!DecompilerContext.getOption(IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS)) {
          buf.setLength(0);  // remove the usual 'new <class>()', it will be replaced with lambda style '() ->'
        }
        Exprent methodObject = constructor == null ? null : constructor.getInstance();
        TextBuffer clsBuf = new TextBuffer();
        new ClassWriter().classLambdaToJava(child, clsBuf, methodObject, indent, tracer);
        buf.append(clsBuf);
        tracer.incrementCurrentSourceLine(clsBuf.countLines());
      }
      else {
        TextBuffer clsBuf = new TextBuffer();
        new ClassWriter().classToJava(child, clsBuf, indent, tracer);
        buf.append(clsBuf);
        tracer.incrementCurrentSourceLine(clsBuf.countLines());
      }
    }
    else if (directArrayInit) {
      VarType leftType = newType.decreaseArrayDim();
      buf.append('{');
      for (int i = 0; i < lstArrayElements.size(); i++) {
        if (i > 0) {
          buf.append(", ");
        }
        ExprProcessor.getCastedExprent(lstArrayElements.get(i), leftType, buf, indent, false, tracer);
      }
      buf.append('}');
    }
    else if (newType.arrayDim == 0) {
      if (!enumConst) {
        String enclosing = null;

        if (constructor != null) {
          enclosing = getQualifiedNewInstance(newType.value, constructor.getLstParameters(), indent, tracer);
          if (enclosing != null) {
            buf.append(enclosing).append('.');
          }
        }

        buf.append("new ");

        String typename = ExprProcessor.getTypeName(newType);
        if (enclosing != null) {
          ClassNode newNode = DecompilerContext.getClassProcessor().getMapRootClasses().get(newType.value);
          if (newNode != null) {
            typename = newNode.simpleName;
          }
          else {
            typename = typename.substring(typename.lastIndexOf('.') + 1);
          }
        }
        buf.append(typename);
      }

      if (constructor != null) {
        List<Exprent> lstParameters = constructor.getLstParameters();

        ClassNode newNode = DecompilerContext.getClassProcessor().getMapRootClasses().get(constructor.getClassname());

        List<VarVersionPair> sigFields = null;
        if (newNode != null) { // own class
          if (newNode.getWrapper() != null) {
            sigFields = newNode.getWrapper().getMethodWrapper(CodeConstants.INIT_NAME, constructor.getStringDescriptor()).signatureFields;
          }
          else if (newNode.type == ClassNode.CLASS_MEMBER && (newNode.access & CodeConstants.ACC_STATIC) == 0 && !constructor.getLstParameters().isEmpty()) {
            // member non-static class invoked with enclosing class instance
            sigFields = new ArrayList<>(Collections.nCopies(lstParameters.size(), (VarVersionPair)null));
            sigFields.set(0, new VarVersionPair(-1, 0));
          }
        }

        int start = enumConst ? 2 : 0;
        if (!enumConst || start < lstParameters.size()) {
          buf.append('(');

          boolean firstParam = true;
          for (int i = start; i < lstParameters.size(); i++) {
            if (sigFields == null || sigFields.get(i) == null) {
              Exprent expr = lstParameters.get(i);
              VarType leftType = constructor.getDescriptor().params[i];

              if (i == lstParameters.size() - 1 && expr.getExprType() == VarType.VARTYPE_NULL) {
                ClassNode node = DecompilerContext.getClassProcessor().getMapRootClasses().get(leftType.value);
                if (node != null && node.namelessConstructorStub) {
                  break;  // skip last parameter of synthetic constructor call
                }
              }

              if (!firstParam) {
                buf.append(", ");
              }

              ExprProcessor.getCastedExprent(expr, leftType, buf, indent, true, tracer);

              firstParam = false;
            }
          }

          buf.append(')');
        }
      }
    }
    else {
      buf.append("new ").append(ExprProcessor.getTypeName(newType));

      if (lstArrayElements.isEmpty()) {
        for (int i = 0; i < newType.arrayDim; i++) {
          buf.append('[');
          if (i < lstDims.size()) {
            buf.append(lstDims.get(i).toJava(indent, tracer));
          }
          buf.append(']');
        }
      }
      else {
        for (int i = 0; i < newType.arrayDim; i++) {
          buf.append("[]");
        }

        VarType leftType = newType.decreaseArrayDim();
        buf.append('{');
        for (int i = 0; i < lstArrayElements.size(); i++) {
          if (i > 0) {
            buf.append(", ");
          }
          ExprProcessor.getCastedExprent(lstArrayElements.get(i), leftType, buf, indent, false, tracer);
        }
        buf.append('}');
      }
    }

    return buf;
  }

  private static String getQualifiedNewInstance(String classname, List<Exprent> lstParams, int indent, BytecodeMappingTracer tracer) {
    ClassNode node = DecompilerContext.getClassProcessor().getMapRootClasses().get(classname);

    if (node != null && node.type != ClassNode.CLASS_ROOT && node.type != ClassNode.CLASS_LOCAL
        && (node.access & CodeConstants.ACC_STATIC) == 0) {
      if (!lstParams.isEmpty()) {
        Exprent enclosing = lstParams.get(0);

        boolean isQualifiedNew = false;

        if (enclosing.type == Exprent.EXPRENT_VAR) {
          VarExprent varEnclosing = (VarExprent)enclosing;

          StructClass current_class = ((ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE)).classStruct;
          String this_classname = varEnclosing.getProcessor().getThisVars().get(new VarVersionPair(varEnclosing));

          if (!current_class.qualifiedName.equals(this_classname)) {
            isQualifiedNew = true;
          }
        }
        else {
          isQualifiedNew = true;
        }

        if (isQualifiedNew) {
          return enclosing.toJava(indent, tracer).toString();
        }
      }
    }

    return null;
  }

  @Override
  public void replaceExprent(Exprent oldExpr, Exprent newExpr) {
    if (oldExpr == constructor) {
      constructor = (InvocationExprent)newExpr;
    }

    if (constructor != null) {
      constructor.replaceExprent(oldExpr, newExpr);
    }

    for (int i = 0; i < lstDims.size(); i++) {
      if (oldExpr == lstDims.get(i)) {
        lstDims.set(i, newExpr);
      }
    }

    for (int i = 0; i < lstArrayElements.size(); i++) {
      if (oldExpr == lstArrayElements.get(i)) {
        lstArrayElements.set(i, newExpr);
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof NewExprent)) return false;

    NewExprent ne = (NewExprent)o;
    return InterpreterUtil.equalObjects(newType, ne.getNewType()) &&
           InterpreterUtil.equalLists(lstDims, ne.getLstDims()) &&
           InterpreterUtil.equalObjects(constructor, ne.getConstructor()) &&
           directArrayInit == ne.directArrayInit &&
           InterpreterUtil.equalLists(lstArrayElements, ne.getLstArrayElements());
  }

  public InvocationExprent getConstructor() {
    return constructor;
  }

  public void setConstructor(InvocationExprent constructor) {
    this.constructor = constructor;
  }

  public List<Exprent> getLstDims() {
    return lstDims;
  }

  public VarType getNewType() {
    return newType;
  }

  public List<Exprent> getLstArrayElements() {
    return lstArrayElements;
  }

  public void setLstArrayElements(List<Exprent> lstArrayElements) {
    this.lstArrayElements = lstArrayElements;
  }

  public void setDirectArrayInit(boolean directArrayInit) {
    this.directArrayInit = directArrayInit;
  }

  public boolean isLambda() {
    return lambda;
  }

  public boolean isAnonymous() {
    return anonymous;
  }

  public void setAnonymous(boolean anonymous) {
    this.anonymous = anonymous;
  }

  public void setEnumConst(boolean enumConst) {
    this.enumConst = enumConst;
  }
}
