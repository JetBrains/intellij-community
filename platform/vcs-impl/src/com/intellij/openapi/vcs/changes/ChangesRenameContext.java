/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import java.io.File;
import java.util.Collection;

public class ChangesRenameContext {
  private File myCurrentPath;

  public ChangesRenameContext(File currentPath) {
    myCurrentPath = currentPath;
  }

  public void checkForRename(final Collection<Change> list) {
    for (Change change : list) {
      if (change.getAfterRevision() != null && change.getBeforeRevision() != null) {
        if (change.getAfterRevision().getFile().getIOFile().equals(myCurrentPath) &&
          (! change.getBeforeRevision().getFile().getIOFile().equals(myCurrentPath))) {
          myCurrentPath = change.getBeforeRevision().getFile().getIOFile();
          return;
        }
      }
    }
  }

  public File getCurrentPath() {
    return myCurrentPath;
  }
}
