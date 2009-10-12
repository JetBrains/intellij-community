/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.ApplicationManager;

import java.util.HashSet;
import java.util.Set;

public class BackgroundableActionEnabledHandler {
  private final Set<Object> myInProgress;

  public BackgroundableActionEnabledHandler() {
    myInProgress = new HashSet<Object>();
  }

  public void register(final Object path) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myInProgress.add(path);
  }

  public boolean isInProgress(final Object path) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myInProgress.contains(path);
  }

  public void completed(final Object path) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myInProgress.remove(path);
  }
}
