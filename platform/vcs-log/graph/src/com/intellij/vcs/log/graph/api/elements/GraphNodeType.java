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

public enum GraphNodeType {
  USUAL(0),
  NOT_LOAD_COMMIT(-1),
  GRAY(1);

  private final byte myType;

  GraphNodeType(int type) {
    myType = (byte)type;
  }

  public byte getType() {
    return myType;
  }

  @NotNull
  public static GraphNodeType getByType(byte type) {
    switch (type) {
      case 0: return USUAL;
      case -1: return NOT_LOAD_COMMIT;
      case 1: return GRAY;
    }
    throw new IllegalArgumentException("Unknown type: " + type);
  }

}
