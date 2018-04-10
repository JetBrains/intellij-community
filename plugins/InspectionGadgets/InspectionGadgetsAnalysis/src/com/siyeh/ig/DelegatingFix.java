/*
 * Copyright 2007-2016 Bas Leijdekkers
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
package com.siyeh.ig;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class DelegatingFix extends InspectionGadgetsFix {

  protected final LocalQuickFix delegate;

  public DelegatingFix(LocalQuickFix delegate) {
    this.delegate = delegate;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return delegate.getName();
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    delegate.applyFix(project, descriptor);
  }

  @Override
  public boolean startInWriteAction() {
    return delegate.startInWriteAction();
  }
}