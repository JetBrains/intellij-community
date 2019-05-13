// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.xdebugger;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents position in a source file. Use {@link XDebuggerUtil#createPosition} and {@link XDebuggerUtil#createPositionByOffset} to
 * create instances of this interface.
 * @author nik
 */
public interface XSourcePosition {
  /**
   * @return 0-based number of line
   */
  int getLine();

  /**
   * @return offset from the beginning of file
   */
  int getOffset();

  @NotNull
  VirtualFile getFile();

  @NotNull
  Navigatable createNavigatable(@NotNull Project project);

  static boolean isOnTheSameLine(@Nullable XSourcePosition pos1, @Nullable XSourcePosition pos2) {
    if (pos1 == null || pos2 == null) {
      return false;
    }
    return pos1.getFile().equals(pos2.getFile()) && pos1.getLine() == pos2.getLine();
  }
}
