/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.project.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DefaultProject extends ProjectImpl {
  private static final String TEMPLATE_PROJECT_NAME = "Default (Template) Project";

  protected DefaultProject(@NotNull ProjectManager manager, @NotNull String filePath, boolean optimiseTestLoadSpeed) {
    super(manager, filePath, optimiseTestLoadSpeed, TEMPLATE_PROJECT_NAME);
  }

  @Override
  public boolean isDefault() {
    return true;
  }

  @Override
  public synchronized void dispose() {
    if (!ApplicationManager.getApplication().isDisposeInProgress() && !ApplicationManager.getApplication().isUnitTestMode()) {
      Logger.getInstance(DefaultProject.class).error(new Exception("Too young to die"));
    }

    super.dispose();
  }
}
