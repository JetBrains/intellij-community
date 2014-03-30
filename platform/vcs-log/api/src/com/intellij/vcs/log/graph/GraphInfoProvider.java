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

import java.util.Set;

/**
 * Use this provider to get information from the Graph. <br/>
 * An instance of GraphInfoProvider is obtained via {@link GraphFacade#getInfoProvider()}.
 */
public interface GraphInfoProvider {

  @NotNull
  Set<Integer> getContainingBranches(int visibleRow); // this requires graph iteration => can take some time

  @NotNull
  RowInfo getRowInfo(int visibleRow);

  boolean areLongEdgesHidden();

  /**
   * Some information about row highlighting etc. TBD
   */
  interface RowInfo {
    int getOneOfHeads();
  }

}
