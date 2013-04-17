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

<<<<<<< HEAD
<<<<<<< HEAD
import com.intellij.openapi.externalSystem.service.project.manage.JarDataService;
=======
import com.intellij.openapi.externalSystem.service.project.manage.JarDataManager;
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
import com.intellij.openapi.externalSystem.service.project.manage.JarDataService;
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.service.project.change.MovedJarsPostProcessor;

/**
 * @author Denis Zhdanov
 * @since 1/18/13 2:11 PM
 */
public class TestMovedJarsPostProcessor extends MovedJarsPostProcessor {

<<<<<<< HEAD
<<<<<<< HEAD
  public TestMovedJarsPostProcessor(@NotNull JarDataService manager) {
=======
  public TestMovedJarsPostProcessor(@NotNull JarDataManager manager) {
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
  public TestMovedJarsPostProcessor(@NotNull JarDataService manager) {
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
    super(manager);
  }

  @Override
  public void doMerge(@NotNull Runnable mergeTask, @NotNull Project project) {
    mergeTask.run();
  }
}
