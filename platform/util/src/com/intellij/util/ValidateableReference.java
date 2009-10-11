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
package com.intellij.util;

public class ValidateableReference<T extends Validateable<T>> {
  private T myReferent;

  public ValidateableReference(T referent) {
    myReferent = referent;
  }

  public T get() {
    if (myReferent == null ||myReferent.isValid()) return myReferent;
    myReferent = myReferent.findMe();
    return myReferent;
  }
}