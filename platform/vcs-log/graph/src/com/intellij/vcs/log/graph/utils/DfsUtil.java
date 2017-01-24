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

package com.intellij.vcs.log.graph.utils;

import com.intellij.util.containers.IntStack;
import org.jetbrains.annotations.NotNull;

public class DfsUtil {
  public interface NextNode {
    int NODE_NOT_FOUND = -1;
    int EXIT = -10;

    int fun(int currentNode);
  }

  public static void walk(int startRowIndex, @NotNull NextNode nextNodeFun) {
    IntStack stack = new IntStack();
    stack.push(startRowIndex);

    while (!stack.empty()) {
      int nextNode = nextNodeFun.fun(stack.peek());
      if (nextNode == NextNode.EXIT) return;
      if (nextNode != NextNode.NODE_NOT_FOUND) {
        stack.push(nextNode);
      }
      else {
        stack.pop();
      }
    }
    stack.clear();
  }
}
