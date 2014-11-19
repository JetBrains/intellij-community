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
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;

public class VarVersionPair {

  public final int var;
  public final int version;

  private int hashCode = -1;

  public VarVersionPair(int var, int version) {
    this.var = var;
    this.version = version;
  }

  public VarVersionPair(Integer var, Integer version) {
    this.var = var.intValue();
    this.version = version.intValue();
  }

  public VarVersionPair(VarExprent var) {
    this.var = var.getIndex();
    this.version = var.getVersion();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof VarVersionPair)) return false;

    VarVersionPair paar = (VarVersionPair)o;
    return var == paar.var && version == paar.version;
  }

  @Override
  public int hashCode() {
    if (hashCode == -1) {
      hashCode = this.var * 3 + this.version;
    }
    return hashCode;
  }

  @Override
  public String toString() {
    return "(" + var + "," + version + ")";
  }
}
