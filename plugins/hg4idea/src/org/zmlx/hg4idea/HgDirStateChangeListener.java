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
package org.zmlx.hg4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;

/**
 * HgDirStateChangeListener listens to the changes of .hg/dirstate to catch external commits and other version control events.
 * It updates file statuses of the files in the Changes view on each dirstate change.
 * Other files need not to be updated.
 *
 * @author Kirill Likhodedov
 */
public class HgDirStateChangeListener extends VirtualFileAdapter {

  private final Project myProject;

  public HgDirStateChangeListener(Project project) {
    myProject = project;
  }

  @Override
  public void contentsChanged(VirtualFileEvent event) {
    if (event.getFile().getPath().endsWith(HgVcs.DIRSTATE_FILE_PATH)) {
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    }
  }
  
}
