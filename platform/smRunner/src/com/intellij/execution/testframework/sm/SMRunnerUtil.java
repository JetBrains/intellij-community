// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.UIUtil;

/**
 * @author Roman Chernyatchik
 */
public class SMRunnerUtil {
  private static final Logger LOG = Logger.getInstance(SMRunnerUtil.class.getName());

  private SMRunnerUtil() {
  }

  /**
   * Adds runnable to Event Dispatch Queue
   * if we aren't in UnitTest of Headless environment mode
   * @param runnable Runnable
   */
  public static void addToInvokeLater(final Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
    if (application.isHeadlessEnvironment() && !application.isUnitTestMode()) {
      runnable.run();
    } else {
      UIUtil.invokeLaterIfNeeded(runnable);
    }
  }

  public static void runInEventDispatchThread(final Runnable runnable, final ModalityState state) {
    try {
      ApplicationManager.getApplication().invokeAndWait(runnable, state);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

}
