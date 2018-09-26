// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class GithubIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, GithubIcons.class);
  }

  private static Icon load(String path, Class<?> clazz) {
    return IconLoader.getIcon(path, clazz);
  }

  public static final Icon DefaultAvatar_40 = load("/org/jetbrains/plugins/github/defaultAvatar_40.svg"); // 40x40
  public static final Icon PullRequestClosed = load("/org/jetbrains/plugins/github/pullRequestClosed.svg"); // 16x16
  public static final Icon PullRequestOpen = load("/org/jetbrains/plugins/github/pullRequestOpen.svg"); // 16x16
  public static final Icon PullRequestsToolWindow = load("/org/jetbrains/plugins/github/pullRequestsToolWindow.svg"); // 13x13

  /** @deprecated to be removed in IDEA 2020 - use AllIcons.Vcs.Vendors.Github */
  @SuppressWarnings("unused")
  @Deprecated
  public static final Icon Github_icon = load("/vcs/vendors/github.svg", com.intellij.icons.AllIcons.class);
}
