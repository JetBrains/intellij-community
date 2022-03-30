// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  // do not change order
  enum RegistrationType {
    ACTION,

    APPLICATION_COMPONENT,
    PROJECT_COMPONENT,
    MODULE_COMPONENT,

    ACTION_ID,
    ACTION_GROUP_ID
  }
}
