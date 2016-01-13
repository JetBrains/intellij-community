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

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 */
public interface GraphCommit<Id> {

  @NotNull
  Id getId();

  @NotNull
  List<Id> getParents();

  /**
   * <p>Returns the timestamp indicating the date & time when this commit was made.</p>
   * <p>This time is displayed in the table by default;
   *    it is used for joining commits from different repositories;
   *    it is used for ordering commits in a single repository (keeping the preference of the topological ordering of course).</p>
   */
  long getTimestamp();

}
