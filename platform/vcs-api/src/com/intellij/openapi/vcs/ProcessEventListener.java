/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import java.util.EventListener;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 3:47 PM
 */
public interface ProcessEventListener extends EventListener {
  /**
   * This method is invoked when git process is terminated
   *
   * @param exitCode a exit code
   */
  void processTerminated(int exitCode);

  /**
   * This method is invoked if starting git process failed with exception
   *
   * @param exception an exception
   */
  void startFailed(Throwable exception);
}
