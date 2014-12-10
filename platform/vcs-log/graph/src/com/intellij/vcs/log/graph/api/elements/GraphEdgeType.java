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

public enum GraphEdgeType {
  USUAL(true),  // between visible delegate nodes
  DOTTED(true), // collapsed fragment
  NOT_LOAD_COMMIT(false), // edge to not load commit
  DOTTED_ARROW_UP(false),
  DOTTED_ARROW_DOWN(false);

  private final boolean myIsNormalEdge;

  GraphEdgeType(boolean isNormalEdge) {
    this.myIsNormalEdge = isNormalEdge;
  }

  public boolean isNormalEdge() {
    return myIsNormalEdge;
  }
}
