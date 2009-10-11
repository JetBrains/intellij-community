/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.ExecutionEvent;
import org.jetbrains.annotations.NonNls;

public final class ExecuteAfterCompilationEvent extends ExecutionEvent {
  @NonNls public static final String TYPE_ID = "afterCompilation";

  private static final ExecuteAfterCompilationEvent ourInstance = new ExecuteAfterCompilationEvent();

  private ExecuteAfterCompilationEvent() {
  }

  public static ExecuteAfterCompilationEvent getInstance() {
    return ourInstance;
  }

  public String getTypeId() {
    return TYPE_ID;
  }

  public String getPresentableName() {
    return AntBundle.message("ant.event.after.compilation.presentable.name");
  }
}
