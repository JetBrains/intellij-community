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
package com.intellij.structuralsearch;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class DefaultMatchResultSink implements MatchResultSink {
  @Override
  public void newMatch(@NotNull MatchResult result) {

  }

  @Override
  public void processFile(@NotNull PsiFile element) {

  }

  @Override
  public void setMatchingProcess(@NotNull MatchingProcess matchingProcess) {

  }

  @Override
  public void matchingFinished() {

  }

  @Override
  public ProgressIndicator getProgressIndicator() {
    return null;
  }
}
