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
package org.jetbrains.android;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.compiler.AndroidPrecompileTask;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidProjectComponent extends AbstractProjectComponent {
  private volatile boolean myCompilationRunning = false;
  private final Object COMPILATION_FLAG_LOCK = new Object();

  protected AndroidProjectComponent(Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    final CompilerManager manager = CompilerManager.getInstance(myProject);
    manager.addBeforeTask(new AndroidPrecompileTask(this));
  }

  public void setCompilationStarted() {
    synchronized (COMPILATION_FLAG_LOCK) {
      myCompilationRunning = true;
    }
  }

  public void setCompilationFinished() {
    synchronized (COMPILATION_FLAG_LOCK) {
      myCompilationRunning = false;
    }
  }

  public void runIfNotInCompilation(@NotNull Runnable r) {
    synchronized (COMPILATION_FLAG_LOCK) {
      if (!myCompilationRunning) {
        r.run();
      }
    }
  }
}
