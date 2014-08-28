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

import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.ArrayList;
import java.util.List;


public class ArrayExprent extends Exprent {

  private Exprent array;

  private Exprent index;

  private VarType hardtype;

  {
    this.type = EXPRENT_ARRAY;
  }

  public ArrayExprent(Exprent array, Exprent index, VarType hardtype) {
    this.array = array;
    this.index = index;
    this.hardtype = hardtype;
  }

  public Exprent copy() {
    return new ArrayExprent(array.copy(), index.copy(), hardtype);
  }

  public VarType getExprType() {
    VarType exprType = array.getExprType().copy();
    if (exprType.equals(VarType.VARTYPE_NULL)) {
      exprType = hardtype.copy();
    }
    else {
      exprType.decArrayDim();
    }

    return exprType;
  }

  public int getExprentUse() {
    return array.getExprentUse() & index.getExprentUse() & Exprent.MULTIPLE_USES;
  }

  public CheckTypesResult checkExprTypeBounds() {
    CheckTypesResult result = new CheckTypesResult();

    result.addMinTypeExprent(index, VarType.VARTYPE_BYTECHAR);
    result.addMaxTypeExprent(index, VarType.VARTYPE_INT);

    return result;
  }

  public List<Exprent> getAllExprents() {
    List<Exprent> lst = new ArrayList<Exprent>();
    lst.add(array);
    lst.add(index);
    return lst;
  }


  public String toJava(int indent) {
    String res = array.toJava(indent);

    if (array.getPrecedence() > getPrecedence()) { // array precedence equals 0
      res = "(" + res + ")";
    }

    VarType arrtype = array.getExprType();
    if (arrtype.arraydim == 0) {
      VarType objarr = VarType.VARTYPE_OBJECT.copy();
      objarr.arraydim = 1; // type family does not change

      res = "((" + ExprProcessor.getCastTypeName(objarr) + ")" + res + ")";
    }

    return res + "[" + index.toJava(indent) + "]";
  }

  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof ArrayExprent)) return false;

    ArrayExprent arr = (ArrayExprent)o;
    return InterpreterUtil.equalObjects(array, arr.getArray()) &&
           InterpreterUtil.equalObjects(index, arr.getIndex());
  }

  public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
    if (oldexpr == array) {
      array = newexpr;
    }

    if (oldexpr == index) {
      index = newexpr;
    }
  }

  public Exprent getArray() {
    return array;
  }

  public Exprent getIndex() {
    return index;
  }
}
