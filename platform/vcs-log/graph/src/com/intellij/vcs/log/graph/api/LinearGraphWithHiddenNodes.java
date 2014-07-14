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
package com.intellij.vcs.log.graph.api;

import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.utils.ListenerController;
import org.jetbrains.annotations.NotNull;

/**
 * In this interface all nodeIndexes is indexes in PermanentGraph.
 *
 * @author erokhins
 */
public interface LinearGraphWithHiddenNodes extends LinearGraphWithElementInfo {

  boolean nodeIsVisible(int nodeIndex);

  @NotNull
  ListenerController<UpdateListener> getListenerController();

  interface UpdateListener {
    // some nodes change visibility in this interval
    void update(int upNodeIndex, int downNodeIndex);
  }
}
