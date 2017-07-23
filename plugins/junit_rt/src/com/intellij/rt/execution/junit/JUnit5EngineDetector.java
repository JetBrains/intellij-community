/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.rt.execution.junit;

import java.util.Iterator;
import java.util.ServiceLoader;

public class JUnit5EngineDetector {

  public static boolean hasCustomEngine() {
    try {
      Iterator iterator = ServiceLoader.load(Class.forName("org.junit.platform.engine.TestEngine")).iterator();
      while (iterator.hasNext()) {
        Object engine = iterator.next();
        String engineClassName = engine.getClass().getName();
        if (!"org.junit.jupiter.engine.JupiterTestEngine".equals(engineClassName) &&
            !"org.junit.vintage.engine.VintageTestEngine".equals(engineClassName)) {
          return true;
        }
      }
      return false;
    }
    catch (Throwable e) {
      return false;
    }
  }
}
