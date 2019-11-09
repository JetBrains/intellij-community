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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;

import java.util.List;

public class VcsInvalidated {
  private final List<VcsDirtyScope> myScopes;
  private final boolean myEverythingDirty;

  public VcsInvalidated(final List<VcsDirtyScope> scopes, final boolean everythingDirty) {
    myScopes = scopes;
    myEverythingDirty = everythingDirty;
  }

  public List<VcsDirtyScope> getScopes() {
    return myScopes;
  }

  public boolean isEverythingDirty() {
    return myEverythingDirty;
  }

  public boolean isEmpty() {
    return myScopes.isEmpty();
  }

  public boolean isFileDirty(final FilePath fp) {
    if (myEverythingDirty) return true;

    for (VcsDirtyScope scope : myScopes) {
      if (scope.belongsTo(fp)) return true;
    }
    return false;
  }
}
