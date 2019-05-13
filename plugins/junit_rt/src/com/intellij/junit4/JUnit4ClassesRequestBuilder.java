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

package com.intellij.junit4;

import org.junit.runner.Request;

public class JUnit4ClassesRequestBuilder {
  public static Request getClassesRequest(String suiteName, Class[] classes) {
    try {
      return (Request)Class.forName("org.junit.internal.requests.ClassesRequest")
                    .getConstructor(new Class[]{String.class, Class[].class})
                    .newInstance(new Object[]{suiteName, classes});
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
}