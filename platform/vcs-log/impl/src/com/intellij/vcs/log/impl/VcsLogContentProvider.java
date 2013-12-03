/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Provides the Content tab to the ChangesView log toolwindow.
 *
 * Delegates to the VcsLogManager.
 */
public class VcsLogContentProvider implements ChangesViewContentProvider, NotNullFunction<Project, Boolean> {

  public static final String TAB_NAME = "Log";

  @NotNull private final VcsLogManager myLogManager;

  public VcsLogContentProvider(@NotNull VcsLogManager logManager) {
    myLogManager = logManager;
  }

  @NotNull
  @Override
  public Boolean fun(Project project) {
    if (!Registry.is("git.new.log")) {
      return false;
    }
    return !myLogManager.findLogProviders().isEmpty();
  }

  @Override
  public JComponent initContent() {
    return myLogManager.initContent();
  }

  @Override
  public void disposeContent() {
  }


}
