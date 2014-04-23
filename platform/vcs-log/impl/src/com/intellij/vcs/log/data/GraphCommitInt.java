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
package com.intellij.vcs.log.data;

import com.intellij.vcs.log.graph.GraphCommit;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GraphCommitInt implements GraphCommit<Integer> {

  private final int myId;
  @NotNull private final List<Integer> myParents;
  private final long myTimestamp;

  public GraphCommitInt(int id, @NotNull List<Integer> parents, long timestamp) {
    myId = id;
    myParents = parents;
    myTimestamp = timestamp;
  }

  @NotNull
  @Override
  public Integer getId() {
    return myId;
  }

  @NotNull
  @Override
  public List<Integer> getParents() {
    return myParents;
  }

  @Override
  public long getTimestamp() {
    return myTimestamp;
  }
}
