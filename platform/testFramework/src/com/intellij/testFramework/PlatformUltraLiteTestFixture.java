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
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

public class PlatformUltraLiteTestFixture {
  @NotNull
  public static PlatformUltraLiteTestFixture getFixture() {
    return new PlatformUltraLiteTestFixture();
  }

  private final Disposable myAppDisposable = Disposer.newDisposable();

  private PlatformUltraLiteTestFixture() { }

  public void setUp() {
    final Application application = ApplicationManager.getApplication();
    if (application == null) {
      ApplicationImpl testapp = new ApplicationImpl(false, true, true, true, "testapp", null);
      ApplicationManager.setApplication(testapp, myAppDisposable);
    }
  }

  public void tearDown() {
    Disposer.dispose(myAppDisposable);
  }
}
