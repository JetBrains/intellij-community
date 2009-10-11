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

/*
 * User: anna
 * Date: 11-Jun-2009
 */
package com.intellij.junit4;

import org.junit.runner.Computer;
import org.junit.runner.Request;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

public class JUnit46ClassesRequestBuilder {
  
  public static Request getClassesRequest(final String suiteName, Class[] classes) {
    return Request.classes(new Computer() {
      public Suite getSuite(final RunnerBuilder builder, Class[] classes) throws InitializationError {
        return new IdeaSuite(builder, classes, suiteName);
      }
    }, classes);
  }

}