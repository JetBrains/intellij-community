// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.util.PlatformUtils;

@SuppressWarnings({"UnusedDeclaration"})
public class MainImpl {
  private MainImpl() { }

  /**
   * Called from PluginManager via reflection.
   */
  protected static void start(final String[] args) throws Exception {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, PlatformUtils.getPlatformPrefix(PlatformUtils.IDEA_CE_PREFIX));

    StartupUtil.prepareAndStart(args, newConfigFolder -> IdeaApplication.initApplication(args));
  }
}