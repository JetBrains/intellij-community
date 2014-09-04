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
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.ArrayList;
import java.util.List;


public class AssignmentExprent extends Exprent {

  public static final int CONDITION_NONE = -1;

  private static final String[] funceq = new String[]{
    " += ",        //  FUNCTION_ADD
    " -= ",        //	FUNCTION_SUB
    " *= ",        //	FUNCTION_MUL
    " /= ",        //	FUNCTION_DIV
    " &= ",        //	FUNCTION_AND
    " |= ",        //	FUNCTION_OR
    " ^= ",        //	FUNCTION_XOR
    " %= ",        //	FUNCTION_REM
    " <<= ",        //	FUNCTION_SHL
    " >>= ",        //	FUNCTION_SHR
    " >>>= "        //	FUNCTION_USHR
  };


  private Exprent left;

  private Exprent right;

  private int condtype = CONDITION_NONE;

  {
    this.type = EXPRENT_ASSIGNMENT;
  }


  public AssignmentExprent(Exprent left, Exprent right) {
    this.left = left;
    this.right = right;
  }


  public VarType getExprType() {
    return left.getExprType();
  }


  public CheckTypesResult checkExprTypeBounds() {
    CheckTypesResult result = new CheckTypesResult();

    VarType typeleft = left.getExprType();
    VarType typeright = right.getExprType();

    if (typeleft.type_family > typeright.type_family) {
      result.addMinTypeExprent(right, VarType.getMinTypeInFamily(typeleft.type_family));
    }
    else if (typeleft.type_family < typeright.type_family) {
      result.addMinTypeExprent(left, typeright);
    }
    else {
      result.addMinTypeExprent(left, VarType.getCommonSupertype(typeleft, typeright));
    }

    return result;
  }

  public List<Exprent> getAllExprents() {
    List<Exprent> lst = new ArrayList<Exprent>();
    lst.add(left);
    lst.add(right);
    return lst;
  }

  public Exprent copy() {
    return new AssignmentExprent(left.copy(), right.copy());
  }

  public int getPrecedence() {
    return 13;
  }

  public String toJava(int indent) {
    VarType leftType = left.getExprType();
    VarType rightType = right.getExprType();

    boolean fieldInClassInit = false, hiddenField = false;
    if (left.type == Exprent.EXPRENT_FIELD) { // first assignment to a final field. Field name without "this" in front of it
      FieldExprent field = (FieldExprent)left;
      ClassNode node = ((ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE));
      if (node != null) {
        StructField fd = node.classStruct.getField(field.getName(), field.getDescriptor().descriptorString);
        if (fd != null) {
          if (field.isStatic() && fd.hasModifier(CodeConstants.ACC_FINAL)) {
            fieldInClassInit = true;
          }
          if (node.wrapper.getHiddenMembers().contains(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()))) {
            hiddenField = true;
          }
        }
      }
    }

    if (hiddenField) {
      return "";
    }

    StringBuilder buffer = new StringBuilder();

    if (fieldInClassInit) {
      buffer.append(((FieldExprent)left).getName());
    }
    else {
      buffer.append(left.toJava(indent));
    }

    String res = right.toJava(indent);

    if (condtype == CONDITION_NONE &&
        !leftType.isSuperset(rightType) &&
        (rightType.equals(VarType.VARTYPE_OBJECT) || leftType.type != CodeConstants.TYPE_OBJECT)) {
      if (right.getPrecedence() >= FunctionExprent.getPrecedence(FunctionExprent.FUNCTION_CAST)) {
        res = "(" + res + ")";
      }

      res = "(" + ExprProcessor.getCastTypeName(leftType) + ")" + res;
    }

    buffer.append(condtype == CONDITION_NONE ? " = " : funceq[condtype]).append(res);

    return buffer.toString();
  }

  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof AssignmentExprent)) return false;

    AssignmentExprent as = (AssignmentExprent)o;
    return InterpreterUtil.equalObjects(left, as.getLeft()) &&
           InterpreterUtil.equalObjects(right, as.getRight()) &&
           condtype == as.getCondtype();
  }

  public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
    if (oldexpr == left) {
      left = newexpr;
    }

    if (oldexpr == right) {
      right = newexpr;
    }
  }

  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public Exprent getLeft() {
    return left;
  }

  public void setLeft(Exprent left) {
    this.left = left;
  }

  public Exprent getRight() {
    return right;
  }

  public void setRight(Exprent right) {
    this.right = right;
  }

  public int getCondtype() {
    return condtype;
  }

  public void setCondtype(int condtype) {
    this.condtype = condtype;
  }
}
