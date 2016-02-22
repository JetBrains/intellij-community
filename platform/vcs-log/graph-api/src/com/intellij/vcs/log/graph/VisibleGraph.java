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

import com.intellij.vcs.log.graph.actions.ActionController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A part of {@link PermanentGraph} which should be drawn on screen (e.g. with applied filters). <br/>
 * This is one per client (page), all access to VisibleGraph should be synchronized. <br/>
 * It refers to the {@link PermanentGraph}, but it occupies a little on its own.
 */
public interface VisibleGraph<Id> {

  int getVisibleCommitCount();

  @NotNull
  RowInfo<Id> getRowInfo(int visibleRow);

  @Nullable
  Integer getVisibleRowIndex(@NotNull Id id);

  @NotNull
  ActionController<Id> getActionController();

}
