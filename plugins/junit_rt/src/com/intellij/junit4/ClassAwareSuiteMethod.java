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
package com.intellij.junit4;

import org.junit.internal.runners.SuiteMethod;

/**
* User: anna
* Date: 2/22/12
*/
class ClassAwareSuiteMethod extends SuiteMethod {
  private final Class myKlass;

  public ClassAwareSuiteMethod(Class klass) throws Throwable {
    super(klass);
    myKlass = klass;
  }

  public Class getKlass() {
    return myKlass;
  }
}
