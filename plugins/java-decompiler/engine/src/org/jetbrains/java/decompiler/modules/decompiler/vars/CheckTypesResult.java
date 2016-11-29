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
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.ArrayList;
import java.util.List;

public class CheckTypesResult {

  private final List<ExprentTypePair> lstMaxTypeExprents = new ArrayList<>();

  private final List<ExprentTypePair> lstMinTypeExprents = new ArrayList<>();

  public void addMaxTypeExprent(Exprent exprent, VarType type) {
    lstMaxTypeExprents.add(new ExprentTypePair(exprent, type, null));
  }

  public void addMinTypeExprent(Exprent exprent, VarType type) {
    lstMinTypeExprents.add(new ExprentTypePair(exprent, type, null));
  }

  public List<ExprentTypePair> getLstMaxTypeExprents() {
    return lstMaxTypeExprents;
  }

  public List<ExprentTypePair> getLstMinTypeExprents() {
    return lstMinTypeExprents;
  }

  public static class ExprentTypePair {
    public final Exprent exprent;
    public final VarType type;
    public final VarType desttype;

    public ExprentTypePair(Exprent exprent, VarType type, VarType desttype) {
      this.exprent = exprent;
      this.type = type;
      this.desttype = desttype;
    }
  }
}
