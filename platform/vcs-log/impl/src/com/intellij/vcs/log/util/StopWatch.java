/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

public class StopWatch {

  private static final Logger LOG = Logger.getInstance(StopWatch.class);

  private final long myStartTime;
  @NotNull private final String myOperation;

  private StopWatch(@NotNull String operation) {
    myOperation = operation;
    myStartTime = System.currentTimeMillis();
  }

  public static StopWatch start(@NotNull String operation) {
    return new StopWatch(operation);
  }

  public void report() {
    LOG.debug(myOperation + " took " + (System.currentTimeMillis() - myStartTime) + " ms");
  }

}
