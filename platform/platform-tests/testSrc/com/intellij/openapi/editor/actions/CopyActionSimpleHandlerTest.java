/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

public class CopyActionSimpleHandlerTest extends CopyActionTest {
  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.ADD_IMPORTS_ON_PASTE = CodeInsightSettings.NO;
      super.runTestRunnable(testRunnable);
      return null;
    });
  }
}
