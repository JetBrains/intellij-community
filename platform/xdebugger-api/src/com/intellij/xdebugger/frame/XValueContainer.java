/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XValueContainer {
  /**
   * Start computing children of the value. Call {@link XCompositeNode#addChildren(XValueChildrenList, boolean)} to add child nodes.
   * Note that this method is called from the Event Dispatch thread so it should return quickly. 
   * @param node node in the tree
   */
  public void computeChildren(@NotNull XCompositeNode node) {
    node.addChildren(XValueChildrenList.EMPTY, true);
  }
}
