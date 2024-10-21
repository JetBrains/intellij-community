// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public abstract class HandleType {
  private final String myName;
  private final boolean myUseVcs;

  public static final HandleType USE_FILE_SYSTEM = new HandleType(IdeBundle.message("handle.ro.file.status.type.using.file.system"), false) {
    @Override
    public void processFiles(@NotNull Collection<? extends VirtualFile> files,
                             @Nullable String changelist, boolean setChangeListActive) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        List<VirtualFile> toRefresh = new ArrayList<>(files.size());

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

  public abstract void processFiles(@NotNull Collection<? extends VirtualFile> virtualFiles,
                                    @Nullable String changelist, boolean setChangeListActive);

  public List<String> getChangelists() {
    return Collections.emptyList();
  }

  public @Nullable String getDefaultChangelist() {
    return null;
  }
}
