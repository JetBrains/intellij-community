// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class DslErrorReporter {
  public static DslErrorReporter getInstance() {
    return ApplicationManager.getApplication().getService(DslErrorReporter.class);
  }
  public abstract void invokeDslErrorPopup(Throwable e, final Project project, @NotNull VirtualFile vfile);
}
