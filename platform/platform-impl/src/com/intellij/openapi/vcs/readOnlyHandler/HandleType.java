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
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class HandleType {
  private final String myName;
  private final boolean myUseVcs;

  public static final HandleType USE_FILE_SYSTEM = new HandleType(VcsBundle.message("handle.ro.file.status.type.using.file.system"), false) {
    @Override
    public void processFiles(final Collection<VirtualFile> files, String changelist) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          List<VirtualFile> toRefresh = ContainerUtil.newArrayListWithCapacity(files.size());

          for (VirtualFile file : files) {
            try {
              ReadOnlyAttributeUtil.setReadOnlyAttribute(file, false);
              toRefresh.add(file);
            }
            catch (IOException ignored) { }
          }

          if (!toRefresh.isEmpty()) {
            RefreshQueue.getInstance().refresh(false, false, null, toRefresh);
          }
        }
      });
    }
  };

  protected HandleType(String name, boolean useVcs) {
    myName = name;
    myUseVcs = useVcs;
  }

  @Override
  public String toString() {
    return myName;
  }

  public boolean getUseVcs() {
    return myUseVcs;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final HandleType that = (HandleType)o;

    if (myUseVcs != that.myUseVcs) return false;
    if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = (myName != null ? myName.hashCode() : 0);
    result = 31 * result + (myUseVcs ? 1 : 0);
    return result;
  }

  public abstract void processFiles(final Collection<VirtualFile> virtualFiles, @Nullable String changelist);
  
  public List<String> getChangelists() {
    return Collections.emptyList();
  }
  
  @Nullable
  public String getDefaultChangelist() {
    return null;
  }
}