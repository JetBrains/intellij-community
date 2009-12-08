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

import org.junit.internal.builders.AllDefaultPossibilitiesBuilder;
import org.junit.runner.Request;
import org.junit.runner.Runner;
import org.junit.runners.model.InitializationError;

public class JUnit46ClassesRequestBuilder {
  private JUnit46ClassesRequestBuilder() {}

  public static Request getClassesRequest(final String suiteName, Class[] classes) {
    try {
      final AllDefaultPossibilitiesBuilder builder = new AllDefaultPossibilitiesBuilder(true);
      final Runner suite = new IdeaSuite(builder, classes, suiteName);
      return Request.runner(suite);
    }
    catch (InitializationError e) {
      throw new RuntimeException(e);
    }
  }

}