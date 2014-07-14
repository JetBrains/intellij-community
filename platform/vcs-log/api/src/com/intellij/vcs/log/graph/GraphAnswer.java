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
package com.intellij.vcs.log.graph;

import org.jetbrains.annotations.Nullable;

/**
 * The graph returns an instance of the GraphAnswer as a reaction to {@link #performAction(com.intellij.vcs.log.graph.GraphAction)}.
 */
public interface GraphAnswer {

  /**
   * Tells how graph was changed after performing action <br/>
   * {@code null} means that graph didn't change (e.g. when user clicks on the arrow to go to the parent/child commit).
   */
  @Nullable
  GraphChange getGraphChange();

  /**
   * Tells which action should be execute by the client code. <br/>
   * {@code null} means that the graph doesn't expect the client code to execute anything special
   * (although of course it can react somehow on the returned {@link #getGraphChange() GraphChange}.
   */
  @Nullable
  GraphActionRequest getActionRequest();

}
