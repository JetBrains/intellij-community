// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.execution;

import com.intellij.rt.execution.CommandLineWrapper;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NonNls;

final class AntPathUtil {
  @NonNls private static final String IDEA_PREPEND_RTJAR = "idea.prepend.rtjar";

  private AntPathUtil() {
  }

  public static void addRtJar(PathsList pathsList) {
    final String ideaRtJarPath = getIdeaRtJarPath();
    if (Boolean.getBoolean(IDEA_PREPEND_RTJAR)) {
      pathsList.addFirst(ideaRtJarPath);
    }
    else {
      pathsList.addTail(ideaRtJarPath);
    }
  }

  public static String getIdeaRtJarPath() {
    final Class<?> aClass = CommandLineWrapper.class;
    return PathUtil.getJarPathForClass(aClass);
  }
}

