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

import com.intellij.xdebugger.Obsolescent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a node with children in a debugger tree. This interface isn't supposed to be implemented by a plugin.
 *
 * @see XValueContainer
 *
 * @author nik
 */
public interface XCompositeNode extends Obsolescent {
  /**
   * Add children to the node.
   * @param children child nodes to add
   * @param last <code>true</code> if all children added
   */
  void addChildren(List<? extends XValue> children, final boolean last);

  /**
   * Add an ellipsis node ("...") indicating that the node has too many children. If user double-click on that node
   * {@link XValueContainer#computeChildren(XCompositeNode)} method will be called again to add next children.
   * @param remaining number of remaining children or <code>-1</code> if unknown
   */
  void tooManyChildren(int remaining);

  /**
   * Indicates that an error occurs
   * @param errorMessage message describing the error
   */
  void setErrorMessage(@NotNull String errorMessage);
}
