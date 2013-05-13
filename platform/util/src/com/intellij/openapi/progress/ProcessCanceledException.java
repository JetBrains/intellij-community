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
package com.intellij.openapi.progress;

import com.intellij.util.SystemProperties;

public class ProcessCanceledException extends RuntimeException {
  private static boolean ourHasStackTraces = SystemProperties.getBooleanProperty("idea.is.internal", false);

  public ProcessCanceledException() {
    int i = 0;
  }

  public ProcessCanceledException(Throwable cause) {
    super(cause);
  }

  @Override
  public Throwable fillInStackTrace() {
    if (ourHasStackTraces) return super.fillInStackTrace();
    // https://wikis.oracle.com/display/HotSpotInternals/PerformanceTechniques
    // http://www.javaspecialists.eu/archive/Issue129.html
    // http://java-performance.info/throwing-an-exception-in-java-is-very-slow/
    return this;
  }
}
