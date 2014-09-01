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
package com.intellij.vcs.log.graph.api.elements;

import org.jetbrains.annotations.NotNull;

public enum GraphEdgeType {
  USUAL(0, true),  // between visible delegate nodes
  DOTTED(1, false), // collapsed fragment
  NOT_LOAD_COMMIT(-2, true), // edge to not load commit
  DOTTED_ARROW_UP(2, false),
  DOTTED_ARROW_DOWN(3, false);

  private final byte myType;
  private final boolean myIsPermanentEdge;

  GraphEdgeType(int type, boolean isPermanentEdge) {
    this.myIsPermanentEdge = isPermanentEdge;
    this.myType = (byte) type;
  }

  public byte getType() {
    return myType;
  }

  public boolean isPermanentEdge() {
    return myIsPermanentEdge;
  }

  @NotNull
  public static GraphEdgeType getByType(byte type) {
    switch (type) {
      case 0: return USUAL;
      case 1: return DOTTED;
      case 2: return DOTTED_ARROW_UP;
      case 3: return DOTTED_ARROW_DOWN;
      case -2: return NOT_LOAD_COMMIT;
    }
    throw new IllegalArgumentException("Unknown type: " + type);
  }
}
