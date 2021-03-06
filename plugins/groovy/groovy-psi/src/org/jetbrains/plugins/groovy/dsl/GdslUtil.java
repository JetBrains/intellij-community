// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.groovy.GdslFileType;

public final class GdslUtil {
  public static final Key<GroovyClassDescriptor> INITIAL_CONTEXT = Key.create("gdsl.initialContext");

  public static final Condition<VirtualFile> GDSL_FILTER = file -> file.getFileType() == GdslFileType.INSTANCE;

  static volatile boolean ourGdslStopped = false;

  static void stopGdsl() {
    ourGdslStopped = true;
  }
}
