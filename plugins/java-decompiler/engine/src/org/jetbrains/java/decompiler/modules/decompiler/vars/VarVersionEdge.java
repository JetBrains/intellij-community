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

public class VarVersionEdge { // FIXME: can be removed? 

  public static final int EDGE_GENERAL = 0;
  public static final int EDGE_PHANTOM = 1;

  public final int type;

  public final VarVersionNode source;

  public final VarVersionNode dest;

  private final int hashCode;

  public VarVersionEdge(int type, VarVersionNode source, VarVersionNode dest) {
    this.type = type;
    this.source = source;
    this.dest = dest;
    this.hashCode = source.hashCode() ^ dest.hashCode() + type;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof VarVersionEdge)) return false;

    VarVersionEdge edge = (VarVersionEdge)o;
    return type == edge.type && source == edge.source && dest == edge.dest;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public String toString() {
    return source.toString() + " ->" + type + "-> " + dest.toString();
  }
}
