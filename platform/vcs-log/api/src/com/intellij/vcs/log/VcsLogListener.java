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
package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

/**
 * Listens to changes in the log, both in Permanent and Visible parts.
 */
public interface VcsLogListener {

  /**
   * This method is called whether a refresh happened, or filter changed, or the VisibleGraph was rebuilt.
   * <p/>
   * It is called on the EDT thread.
   *
   * @param dataPack        new VcsLogDataPack which was just applied to the UI.
   * @param refreshHappened true if a refresh initiated this log change, i.e. PermanentGraph changed.
   */
  void onChange(@NotNull VcsLogDataPack dataPack, boolean refreshHappened);

}
