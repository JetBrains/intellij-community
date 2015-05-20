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
package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.Packet;
import com.intellij.rt.execution.junit.segments.PoolOfDelimiters;

import java.util.*;

public class TreeSender {

  private TreeSender() {
  }

  public static void sendTree(IdeaTestRunner runner, Object suite, boolean sendTree) {
    if (sendTree) {
      Packet packet = runner.getRegistry().createPacket();
      packet.addString(PoolOfDelimiters.TREE_PREFIX);
      Set objects = new HashSet();
      sendNode(runner, suite, packet, objects);

      for (Iterator iterator = objects.iterator(); iterator.hasNext(); ) {
        ((Packet)iterator.next()).send();
      }
      packet.addString("\n");
      packet.send();
    }
  }

  private static void sendNode(IdeaTestRunner runner, Object test, Packet packet, Collection objectPackets) {
    final List children = runner.getChildTests(test);
    packet.addObject(test, objectPackets).addLong(children.size());
    for (int i = 0; i < children.size(); i++) {
      sendNode(runner, children.get(i), packet, objectPackets);
    }
  }
}
