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
package org.jetbrains.plugins.gradle.util;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.manage.GradleJarManager;
import org.jetbrains.plugins.gradle.sync.GradleMovedJarsPostProcessor;

/**
 * @author Denis Zhdanov
 * @since 1/18/13 2:11 PM
 */
public class TestGradleMovedJarsPostProcessor extends GradleMovedJarsPostProcessor {

  public TestGradleMovedJarsPostProcessor(@NotNull GradleJarManager manager) {
    super(manager);
  }

  @Override
  public void doMerge(@NotNull Runnable mergeTask, @NotNull Project project) {
    mergeTask.run();
  }
}
