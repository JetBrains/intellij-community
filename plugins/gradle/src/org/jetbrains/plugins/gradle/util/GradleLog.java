// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util;

import com.intellij.openapi.diagnostic.Logger;

/**
 * @author Denis Zhdanov
 */
public final class GradleLog {

  public static final Logger LOG = Logger.getInstance(GradleLog.class);

  private GradleLog() {
  }
}
