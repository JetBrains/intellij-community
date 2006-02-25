/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.cvsSupport2.cvsExecution;

public interface ModalityContext {
  void runInDispatchThread(Runnable action);

  boolean isForTemporaryConfiguration();
}
