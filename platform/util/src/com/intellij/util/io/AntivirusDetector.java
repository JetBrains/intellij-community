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

/*
 * @author max
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

public class AntivirusDetector {
  private static final int THRESHOULD = 500;
  private boolean myEnabled = false;
  private Runnable myCallback;
  private static final AntivirusDetector ourInstance = new AntivirusDetector();

  public static AntivirusDetector getInstance() {
    return ourInstance;
  }

  private AntivirusDetector() {}

  public void enable(@NotNull Runnable callback) {
    myCallback = callback;
    myEnabled = true;
  }

  public void disable() {
    myEnabled = false;
  }

  public void execute(Runnable r) {
    if (!myEnabled) {
      r.run();
      return;
    }

    long now = System.currentTimeMillis();
    r.run();
    long delta = System.currentTimeMillis() - now;

    if (delta > THRESHOULD) {
      disable();
      myCallback.run();
    }
  }

}