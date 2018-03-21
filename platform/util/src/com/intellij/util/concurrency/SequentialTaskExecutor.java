/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.util.concurrency;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class SequentialTaskExecutor {
  private SequentialTaskExecutor() {
  }

  @NotNull
  public static ExecutorService createSequentialApplicationPoolExecutor(@NotNull @Nls(capitalization = Nls.Capitalization.Title) String name) {
    return AppExecutorUtil.createBoundedApplicationPoolExecutor(name, 1);
  }

  @NotNull
  public static ExecutorService createSequentialApplicationPoolExecutor(@NotNull @Nls(capitalization = Nls.Capitalization.Title) String name,  @NotNull Executor executor) {
    return AppExecutorUtil.createBoundedApplicationPoolExecutor(name, executor, 1);
  }
}
