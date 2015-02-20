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
package com.intellij.xdebugger.evaluation;

import com.intellij.xdebugger.XNamedTreeNode;

/**
 * @author Konstantin Bulenkov
 */
public abstract class InlineDebuggerHelper {
  public static final InlineDebuggerHelper DEFAULT = new InlineDebuggerHelper() {
    @Override
    public boolean shouldEvaluateChildrenByDefault(XNamedTreeNode node) {
      return "this".equals(node.getName());
    }
  };

  /**
   * If true evaluates children for the node even if it is collapsed. This is
   * necessary for inline debugger to evaluate instance fields of a class
   *
   * @param node debugger tree node
   *
   * @return <code>true</code> to evaluate children for the inline debugger,
   * <code>false</code> otherwise
   */
  public abstract boolean shouldEvaluateChildrenByDefault(XNamedTreeNode node);
}
