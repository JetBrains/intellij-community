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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.ZipperUpdater;
import com.intellij.util.Alarm;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 4/11/12
 * Time: 3:10 PM
 */
public class ConstantZipperUpdater {
  private final ZipperUpdater myZipperUpdater;
  private final Runnable myRunnable;

  public ConstantZipperUpdater(final int delay, final Alarm.ThreadToUse threadToUse, final Disposable parentDisposable,
                               final Runnable runnable) {
    myRunnable = runnable;
    myZipperUpdater = new ZipperUpdater(delay, threadToUse, parentDisposable);
  }

  public void request() {
    myZipperUpdater.queue(myRunnable);
  }
}
