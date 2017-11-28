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
package org.jetbrains.idea.devkit.dom.index;

class RegistrationEntry {

  private final RegistrationType myRegistrationType;
  private final int myOffset;

  RegistrationEntry(RegistrationType registrationType, int offset) {
    myRegistrationType = registrationType;
    myOffset = offset;
  }

  RegistrationType getRegistrationType() {
    return myRegistrationType;
  }

  int getOffset() {
    return myOffset;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RegistrationEntry entry = (RegistrationEntry)o;

    if (myOffset != entry.myOffset) return false;
    if (myRegistrationType != entry.myRegistrationType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myRegistrationType.hashCode();
    result = 31 * result + myOffset;
    return result;
  }

  enum RegistrationType {
    ACTION,
    APPLICATION_COMPONENT,
    PROJECT_COMPONENT,
    MODULE_COMPONENT
  }
}
