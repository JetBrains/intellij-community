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
package com.intellij.cvsSupport2.application;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;

public abstract class CvsStorageComponent extends VirtualFileAdapter {
  protected boolean myIsActive = false;
  public static final CvsStorageComponent ABSENT_STORAGE = new CvsStorageComponent() {

    public void init(Project project, boolean synch) {
    }

    public void dispose() {
    }

    public void projectOpened() {
    }

    public void projectClosed() {
    }

    public void disposeComponent() {
    }

    public void initComponent() { }

    public void deleteIfAdminDirCreated(VirtualFile addedFile) {
    }

    public String getComponentName() {
      return "CvsStorageComponent.Absent";
    }
  };

  public abstract void init(Project project, boolean synch);
  public abstract void dispose();

  public boolean getIsActive() {
    return myIsActive;
  }

  public abstract void deleteIfAdminDirCreated(VirtualFile addedFile);

}
