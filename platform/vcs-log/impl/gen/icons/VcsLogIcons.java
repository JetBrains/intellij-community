// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class VcsLogIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, VcsLogIcons.class);
  }

  private static Icon load(String path, Class<?> clazz) {
    return IconLoader.getIcon(path, clazz);
  }

  /**
   * 16x16
   */
  public static final Icon IntelliSort = load("/icons/IntelliSort.svg");

  public final static class Process {
    /**
     * 16x16
     */
    public static final Icon Dots_1 = load("/icons/process/dots_1.svg");
    /**
     * 16x16
     */
    public static final Icon Dots_2 = load("/icons/process/dots_2.svg");
    /**
     * 16x16
     */
    public static final Icon Dots_3 = load("/icons/process/dots_3.svg");
    /**
     * 16x16
     */
    public static final Icon Dots_4 = load("/icons/process/dots_4.svg");
    /**
     * 16x16
     */
    public static final Icon Dots_5 = load("/icons/process/dots_5.svg");

  }

  /** @deprecated to be removed in IDEA 2020 - use AllIcons.Vcs.Branch */
  @SuppressWarnings("unused")
  @Deprecated
  public static final Icon ShowOtherBranches = load("/vcs/branch.svg", com.intellij.icons.AllIcons.class);
}
