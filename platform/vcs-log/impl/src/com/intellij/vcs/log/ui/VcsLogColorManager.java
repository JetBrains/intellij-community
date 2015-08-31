package com.intellij.vcs.log.ui;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Managers colors used for the vcs log: references, roots, branches, etc.
 *
 * @author Kirill Likhodedov
 */
public interface VcsLogColorManager {

  /**
   * Returns the color assigned to the given repository root.
   */
  @NotNull
  Color getRootColor(@NotNull VirtualFile root);

  /**
   * Tells if there are several repositories currently shown in the log.
   */
  boolean isMultipleRoots();

}
