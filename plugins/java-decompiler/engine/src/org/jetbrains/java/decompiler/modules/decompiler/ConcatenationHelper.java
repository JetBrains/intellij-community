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
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.ArrayList;
import java.util.List;

public class ConcatenationHelper {

  private static final String builderClass = "java/lang/StringBuilder";
  private static final String bufferClass = "java/lang/StringBuffer";
  private static final String stringClass = "java/lang/String";

  private static final VarType builderType = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/StringBuilder");
  private static final VarType bufferType = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/StringBuffer");


  public static Exprent contractStringConcat(Exprent expr) {

    Exprent exprTmp = null;
    VarType cltype = null;

    // first quick test
    if (expr.type == Exprent.EXPRENT_INVOCATION) {
      InvocationExprent iex = (InvocationExprent)expr;
      if ("toString".equals(iex.getName())) {
        if (builderClass.equals(iex.getClassname())) {
          cltype = builderType;
        }
        else if (bufferClass.equals(iex.getClassname())) {
          cltype = bufferType;
        }
        if (cltype != null) {
          exprTmp = iex.getInstance();
        }
      }
    }

    if (exprTmp == null) {
      return expr;
    }


    // iterate in depth, collecting possible operands
    List<Exprent> lstOperands = new ArrayList<Exprent>();

    while (true) {

      int found = 0;

      switch (exprTmp.type) {
        case Exprent.EXPRENT_INVOCATION:
          InvocationExprent iex = (InvocationExprent)exprTmp;
          if (isAppendConcat(iex, cltype)) {
            lstOperands.add(0, iex.getLstParameters().get(0));
            exprTmp = iex.getInstance();
            found = 1;
          }
          break;
        case Exprent.EXPRENT_NEW:
          NewExprent nex = (NewExprent)exprTmp;
          if (isNewConcat(nex, cltype)) {
            VarType[] params = nex.getConstructor().getDescriptor().params;
            if (params.length == 1) {
              lstOperands.add(0, nex.getConstructor().getLstParameters().get(0));
            }
            found = 2;
          }
      }

      if (found == 0) {
        return expr;
      }
      else if (found == 2) {
        break;
      }
    }

    int first2str = 0;
    int index = 0;
    while (index < lstOperands.size() && index < 2) {
      if (lstOperands.get(index).getExprType().equals(VarType.VARTYPE_STRING)) {
        first2str |= (index + 1);
      }
      index++;
    }

    if (first2str == 0) {
      lstOperands.add(0, new ConstExprent(VarType.VARTYPE_STRING, ""));
    }

    // remove redundant String.valueOf
    for (int i = 0; i < lstOperands.size(); i++) {
      Exprent rep = removeStringValueOf(lstOperands.get(i));

      boolean ok = (i > 1);
      if (!ok) {
        boolean isstr = rep.getExprType().equals(VarType.VARTYPE_STRING);
        ok = isstr || first2str != i + 1;

        if (i == 0) {
          first2str &= 2;
        }
      }

      if (ok) {
        lstOperands.set(i, rep);
      }
    }

    // build exprent to return
    Exprent func = lstOperands.get(0);

    for (int i = 1; i < lstOperands.size(); i++) {
      List<Exprent> lstTmp = new ArrayList<Exprent>();
      lstTmp.add(func);
      lstTmp.add(lstOperands.get(i));
      func = new FunctionExprent(FunctionExprent.FUNCTION_STRCONCAT, lstTmp);
    }

    return func;
  }

  private static boolean isAppendConcat(InvocationExprent expr, VarType cltype) {

    if ("append".equals(expr.getName())) {
      MethodDescriptor md = expr.getDescriptor();
      if (md.ret.equals(cltype) && md.params.length == 1) {
        VarType param = md.params[0];
        switch (param.type) {
          case CodeConstants.TYPE_OBJECT:
            if (!param.equals(VarType.VARTYPE_STRING) &&
                !param.equals(VarType.VARTYPE_OBJECT)) {
              break;
            }
          case CodeConstants.TYPE_BOOLEAN:
          case CodeConstants.TYPE_CHAR:
          case CodeConstants.TYPE_DOUBLE:
          case CodeConstants.TYPE_FLOAT:
          case CodeConstants.TYPE_INT:
          case CodeConstants.TYPE_LONG:
            return true;
          default:
        }
      }
    }

    return false;
  }

  private static boolean isNewConcat(NewExprent expr, VarType cltype) {

    if (expr.getNewtype().equals(cltype)) {
      VarType[] params = expr.getConstructor().getDescriptor().params;
      if (params.length == 0 || (params.length == 1 &&
                                 params[0].equals(VarType.VARTYPE_STRING))) {
        return true;
      }
    }

    return false;
  }

  private static Exprent removeStringValueOf(Exprent exprent) {

    if (exprent.type == Exprent.EXPRENT_INVOCATION) {
      InvocationExprent iex = (InvocationExprent)exprent;
      if ("valueOf".equals(iex.getName()) && stringClass.equals(iex.getClassname())) {
        MethodDescriptor md = iex.getDescriptor();
        if (md.params.length == 1) {
          VarType param = md.params[0];
          switch (param.type) {
            case CodeConstants.TYPE_OBJECT:
              if (!param.equals(VarType.VARTYPE_OBJECT)) {
                break;
              }
            case CodeConstants.TYPE_BOOLEAN:
            case CodeConstants.TYPE_CHAR:
            case CodeConstants.TYPE_DOUBLE:
            case CodeConstants.TYPE_FLOAT:
            case CodeConstants.TYPE_INT:
            case CodeConstants.TYPE_LONG:
              return iex.getLstParameters().get(0);
          }
        }
      }
    }

    return exprent;
  }
}
