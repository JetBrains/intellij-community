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
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;

import java.util.ArrayList;
import java.util.List;

public class PrimitiveExprsList {

  private final List<Exprent> lstExprents = new ArrayList<>();

  private ExprentStack stack = new ExprentStack();

  public PrimitiveExprsList() {
  }

  public PrimitiveExprsList copyStack() {
    PrimitiveExprsList prlst = new PrimitiveExprsList();
    prlst.setStack(stack.clone());
    return prlst;
  }

  public List<Exprent> getLstExprents() {
    return lstExprents;
  }

  public ExprentStack getStack() {
    return stack;
  }

  public void setStack(ExprentStack stack) {
    this.stack = stack;
  }
}
