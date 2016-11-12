/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.application.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author peter
 */
class NoSwingUnderWriteAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.impl.NoSwingUnderWriteAction");

  static void watchForEvents(Application application) {
    AtomicBoolean reported = new AtomicBoolean();
    IdeEventQueue.getInstance().addPostprocessor(e -> {
      if (application.isWriteAccessAllowed() && reported.compareAndSet(false, true)) {
        LOG.error("AWT events are not allowed inside write action: " + e);
      }
      return true;
    }, application);

    application.addApplicationListener(new ApplicationAdapter() {
      @Override
      public void afterWriteActionFinished(@NotNull Object action) {
        reported.set(false);
      }
    });
  }
}
