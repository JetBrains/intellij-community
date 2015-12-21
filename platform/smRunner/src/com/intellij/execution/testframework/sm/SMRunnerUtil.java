/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.testframework.sm;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;

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

  public static void registerAsAction(final KeyStroke keyStroke,
                                      final String actionKey,
                                      final Runnable action, final JComponent component) {
    final InputMap inputMap = component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    inputMap.put(keyStroke, actionKey);
    component.getActionMap().put(inputMap.get(keyStroke), new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        action.run();
      }
    });
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
