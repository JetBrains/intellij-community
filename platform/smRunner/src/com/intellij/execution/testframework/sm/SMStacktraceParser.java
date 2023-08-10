/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.TestStackTraceParser;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public interface SMStacktraceParser {
  /**
   * Used for navigation from tests view to the editor if "open failed line" option is selected
   */
  @Nullable
  Navigatable getErrorNavigatable(@NotNull Location<?> location, @NotNull String stacktrace);

  default TestStackTraceParser getTestStackTraceParser(@NotNull String url, @NotNull SMTestProxy proxy, @NotNull Project project) {
    return new TestStackTraceParser(url, proxy.getStacktrace(), proxy.getErrorMessage(), proxy.getLocator(), project);
  }
}
