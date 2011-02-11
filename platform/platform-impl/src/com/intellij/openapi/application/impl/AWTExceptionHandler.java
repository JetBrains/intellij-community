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
package com.intellij.openapi.application.impl;

public class AWTExceptionHandler {
  public static void register() {
    System.setProperty("sun.awt.exception.handler", AWTExceptionHandler.class.getName());
  }

  // the method must be here
  @SuppressWarnings({"UnusedParameters"})
  public void handle(Throwable e) {
    // error has already been logged
    // do nothing, do not crash AWT
  }
}
