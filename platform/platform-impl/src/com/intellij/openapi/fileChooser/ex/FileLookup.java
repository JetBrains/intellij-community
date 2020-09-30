// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public interface FileLookup {
  interface Finder {
    @Nullable LookupFile find(@NotNull String path);

    String normalize(@NotNull String path);

    @NlsSafe String getSeparator();
  }

  interface LookupFile {
    @NlsSafe String getName();
    String getAbsolutePath();
    boolean isDirectory();

    void setMacro(String macro);
    @NlsSafe @Nullable String getMacro();

    List<LookupFile> getChildren(LookupFilter filter);

    @Nullable LookupFile getParent();

    boolean exists();

    @Nullable Icon getIcon();
  }

  interface LookupFilter {
    boolean isAccepted(LookupFile file);
  }
}
