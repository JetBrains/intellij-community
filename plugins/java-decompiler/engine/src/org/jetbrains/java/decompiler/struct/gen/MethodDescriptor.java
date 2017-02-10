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
package org.jetbrains.java.decompiler.struct.gen;

import org.jetbrains.java.decompiler.code.CodeConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MethodDescriptor {

  public final VarType[] params;
  public final VarType ret;

  private MethodDescriptor(VarType[] params, VarType ret) {
    this.params = params;
    this.ret = ret;
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
          case '[':
            if (indexFrom < 0) {
              indexFrom = index;
            }
            break;
          case 'L':
            ind = parameters.indexOf(";", index);
            lst.add(parameters.substring(indexFrom < 0 ? index : indexFrom, ind + 1));
            index = ind;
            indexFrom = -1;
            break;
          default:
            lst.add(parameters.substring(indexFrom < 0 ? index : indexFrom, index + 1));
            indexFrom = -1;
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

    return new MethodDescriptor(params, ret);
  }

  public String buildNewDescriptor(NewClassNameBuilder builder) {
    boolean updated = false;

    VarType[] newParams;
    if (params.length > 0) {
      newParams = new VarType[params.length];
      System.arraycopy(params, 0, newParams, 0, params.length);
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
    if (type.type == CodeConstants.TYPE_OBJECT) {
      String newClassName = builder.buildNewClassname(type.value);
      if (newClassName != null) {
        return new VarType(type.type, type.arrayDim, newClassName);
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof MethodDescriptor)) return false;

    MethodDescriptor md = (MethodDescriptor)o;
    return ret.equals(md.ret) && Arrays.equals(params, md.params);
  }

  @Override
  public int hashCode() {
    int result = ret.hashCode();
    result = 31 * result + params.length;
    return result;
  }
}
