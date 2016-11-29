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
package org.jetbrains.java.decompiler.modules.decompiler.sforms;

import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper.FinallyPathWrapper;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;


public class DirectGraph {

  public final VBStyleCollection<DirectNode, String> nodes = new VBStyleCollection<>();

  public DirectNode first;

  // exit, [source, destination]
  public final HashMap<String, List<FinallyPathWrapper>> mapShortRangeFinallyPaths = new HashMap<>();

  // exit, [source, destination]
  public final HashMap<String, List<FinallyPathWrapper>> mapLongRangeFinallyPaths = new HashMap<>();

  // negative if branches (recorded for handling of && and ||)
  public final HashMap<String, String> mapNegIfBranch = new HashMap<>();

  // nodes, that are exception exits of a finally block with monitor variable
  public final HashMap<String, String> mapFinallyMonitorExceptionPathExits = new HashMap<>();

  public void sortReversePostOrder() {
    LinkedList<DirectNode> res = new LinkedList<>();
    addToReversePostOrderListIterative(first, res);

    nodes.clear();
    for (DirectNode node : res) {
      nodes.addWithKey(node, node.id);
    }
  }

  private static void addToReversePostOrderListIterative(DirectNode root, List<DirectNode> lst) {

    LinkedList<DirectNode> stackNode = new LinkedList<>();
    LinkedList<Integer> stackIndex = new LinkedList<>();

    HashSet<DirectNode> setVisited = new HashSet<>();

    stackNode.add(root);
    stackIndex.add(0);

    while (!stackNode.isEmpty()) {

      DirectNode node = stackNode.getLast();
      int index = stackIndex.removeLast();

      setVisited.add(node);

      for (; index < node.succs.size(); index++) {
        DirectNode succ = node.succs.get(index);

        if (!setVisited.contains(succ)) {
          stackIndex.add(index + 1);

          stackNode.add(succ);
          stackIndex.add(0);

          break;
        }
      }

      if (index == node.succs.size()) {
        lst.add(0, node);

        stackNode.removeLast();
      }
    }
  }


  public boolean iterateExprents(ExprentIterator iter) {

    LinkedList<DirectNode> stack = new LinkedList<>();
    stack.add(first);

    HashSet<DirectNode> setVisited = new HashSet<>();

    while (!stack.isEmpty()) {

      DirectNode node = stack.removeFirst();

      if (setVisited.contains(node)) {
        continue;
      }
      setVisited.add(node);

      for (int i = 0; i < node.exprents.size(); i++) {
        int res = iter.processExprent(node.exprents.get(i));

        if (res == 1) {
          return false;
        }

        if (res == 2) {
          node.exprents.remove(i);
          i--;
        }
      }

      stack.addAll(node.succs);
    }

    return true;
  }

  public interface ExprentIterator {
    // 0 - success, do nothing
    // 1 - cancel iteration
    // 2 - success, delete exprent
    int processExprent(Exprent exprent);
  }
}
