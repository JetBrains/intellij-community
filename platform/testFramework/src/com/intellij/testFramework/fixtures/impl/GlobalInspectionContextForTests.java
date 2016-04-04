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
package com.intellij.testFramework.fixtures.impl;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class GlobalInspectionContextForTests extends GlobalInspectionContextImpl {
  private volatile boolean myFinished;

  public GlobalInspectionContextForTests(@NotNull Project project, @NotNull NotNullLazyValue<ContentManager> contentManager) {
    super(project, contentManager);
  }

  @Override
  protected void notifyInspectionsFinished(AnalysisScope scope) {
    super.notifyInspectionsFinished(scope);
    myFinished = true;
  }

  public boolean isFinished() {
    return myFinished;
  }
}
