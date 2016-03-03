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
package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLogProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

class DataPackBase {
  @NotNull protected final Map<VirtualFile, VcsLogProvider> myLogProviders;
  @NotNull protected final RefsModel myRefsModel;
  protected final boolean myIsFull;

  DataPackBase(@NotNull Map<VirtualFile, VcsLogProvider> providers, @NotNull RefsModel refsModel, boolean isFull) {
    myLogProviders = providers;
    myRefsModel = refsModel;
    myIsFull = isFull;
  }

  @NotNull
  public Map<VirtualFile, VcsLogProvider> getLogProviders() {
    return myLogProviders;
  }

  @NotNull
  public RefsModel getRefsModel() {
    return myRefsModel;
  }

  public boolean isFull() {
    return myIsFull;
  }
}
