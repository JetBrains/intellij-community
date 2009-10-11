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
package com.intellij.openapi.application.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;

/**
 * @author max
 */
public class ApplicationManagerEx extends ApplicationManager {
  public static ApplicationEx getApplicationEx() {
    return (ApplicationEx) ourApplication;
  }

  public static void createApplication(boolean internal, boolean isUnitTestMode, boolean isHeadlessMode, boolean isCommandline, @NonNls String appName) {
    new ApplicationImpl(internal, isUnitTestMode, isHeadlessMode, isCommandline, appName);
  }

  public static void setApplication(Application instance) {
    ourApplication = instance;
    CachedSingletonsRegistry.cleanupCachedFields();
  }

  public static void setApplication(Application instance, Disposable parent) {
    final Application old = ourApplication;
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        ourApplication = old;
        CachedSingletonsRegistry.cleanupCachedFields();
      }
    });
    ourApplication = instance;
    CachedSingletonsRegistry.cleanupCachedFields();
  }
}
