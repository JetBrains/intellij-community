// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

    default @NotNull List<String> split(@NotNull String path) {
      return List.of(normalize(path).split(getSeparator().replaceAll("\\\\", "\\\\\\\\")));
    }
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
