// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct.gen;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericMethodDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class MethodDescriptor {
  public final VarType[] params;
  public final VarType ret;
  private final String descriptor;
  public GenericMethodDescriptor genericInfo;

  private MethodDescriptor(VarType[] params, VarType ret, String descriptor) {
    this.params = params;
    this.ret = ret;
    this.descriptor = descriptor;
  }

  public static MethodDescriptor parseDescriptor(String descriptor) {
    int parenth = descriptor.lastIndexOf(')');
    if (descriptor.length() < 2 || parenth < 0 || descriptor.charAt(0) != '(') {
      throw new IllegalArgumentException("Invalid descriptor: " + descriptor);
    }

    VarType[] params;

    if (parenth > 1) {
      String parameters = descriptor.substring(1, parenth);
      List<String> lst = new ArrayList<>();

      int indexFrom = -1, ind, len = parameters.length(), index = 0;
      while (index < len) {
        switch (parameters.charAt(index)) {
          case '[' -> {
            if (indexFrom < 0) {
              indexFrom = index;
            }
          }
          case 'L' -> {
            ind = parameters.indexOf(";", index);
            lst.add(parameters.substring(indexFrom < 0 ? index : indexFrom, ind + 1));
            index = ind;
            indexFrom = -1;
          }
          default -> {
            lst.add(parameters.substring(indexFrom < 0 ? index : indexFrom, index + 1));
            indexFrom = -1;
          }
        }
        index++;
      }

      params = new VarType[lst.size()];
      for (int i = 0; i < lst.size(); i++) {
        params[i] = new VarType(lst.get(i));
      }
    }
    else {
      params = VarType.EMPTY_ARRAY;
    }

    VarType ret = new VarType(descriptor.substring(parenth + 1));

    return new MethodDescriptor(params, ret, descriptor);
  }

  public static MethodDescriptor parseDescriptor(StructMethod struct, ClassNode node) {
    MethodDescriptor md = MethodDescriptor.parseDescriptor(struct.getDescriptor());

    GenericMethodDescriptor sig = struct.getSignature();
    if (sig != null) {
      if (node != null) {
        MethodWrapper methodWrapper = node.getWrapper().getMethodWrapper(struct.getName(), struct.getDescriptor());
        boolean init = CodeConstants.INIT_NAME.equals(struct.getName()) && node.type != ClassNode.CLASS_ANONYMOUS;
        long actualParams = md.params.length;
        List<VarVersionPair> sigFields = methodWrapper == null ? null : methodWrapper.synthParameters;
        if (sigFields != null) {
          actualParams = sigFields.stream().filter(Objects::isNull).count();
        }
        if (actualParams != sig.parameterTypes.size()) {
          String message = "Inconsistent generic signature in method " + struct.getName() + " " + struct.getDescriptor() + " in " + struct.getClassQualifiedName();
          DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
          sig = null;
        }
      }
      md.addGenericDescriptor(sig);
    }

    return md;
  }

  public void addGenericDescriptor(GenericMethodDescriptor desc) {
    this.genericInfo = desc;
  }

  public String buildNewDescriptor(NewClassNameBuilder builder) {
    boolean updated = false;

    VarType[] newParams;
    if (params.length > 0) {
      newParams = params.clone();
      for (int i = 0; i < params.length; i++) {
        VarType substitute = buildNewType(params[i], builder);
        if (substitute != null) {
          newParams[i] = substitute;
          updated = true;
        }
      }
    }
    else {
      newParams = VarType.EMPTY_ARRAY;
    }

    VarType newRet = ret;
    VarType substitute = buildNewType(ret, builder);
    if (substitute != null) {
      newRet = substitute;
      updated = true;
    }

    if (updated) {
      StringBuilder res = new StringBuilder("(");
      for (VarType param : newParams) {
        res.append(param);
      }
      res.append(")").append(newRet.toString());
      return res.toString();
    }

    return null;
  }

  private static VarType buildNewType(VarType type, NewClassNameBuilder builder) {
    if (type.getType() == CodeConstants.TYPE_OBJECT) {
      String newClassName = builder.buildNewClassname(type.getValue());
      if (newClassName != null) {
        return new VarType(type.getType(), type.getArrayDim(), newClassName);
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return this.descriptor;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof MethodDescriptor md)) return false;

    return ret.equals(md.ret) && Arrays.equals(params, md.params);
  }

  @Override
  public int hashCode() {
    int result = ret.hashCode();
    result = 31 * result + params.length;
    return result;
  }
}
