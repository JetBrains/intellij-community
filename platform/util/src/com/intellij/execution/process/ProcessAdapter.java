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
package com.intellij.execution.process;

import com.intellij.openapi.util.Key;

public abstract class ProcessAdapter implements ProcessListener{
  @Override
  public void startNotified(final ProcessEvent event) {
  }

  @Override
  public void processTerminated(final ProcessEvent event) {
  }

  @Override
  public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
  }

  @Override
  public void onTextAvailable(final ProcessEvent event, final Key outputType) {
  }
}