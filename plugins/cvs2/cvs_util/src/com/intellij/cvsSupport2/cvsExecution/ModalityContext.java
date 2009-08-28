/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.cvsSupport2.cvsExecution;

import com.intellij.openapi.project.Project;

public interface ModalityContext {
  void runInDispatchThread(Runnable action, Project project);

  boolean isForTemporaryConfiguration();
}
