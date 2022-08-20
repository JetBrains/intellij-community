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

  // update IdeaPluginRegistrationIndex.INDEX_VERSION
  enum RegistrationType {
    ACTION(true),

    APPLICATION_COMPONENT(true),
    PROJECT_COMPONENT(true),
    MODULE_COMPONENT(true),
    COMPONENT_INTERFACE(true),

    ACTION_ID(false),
    ACTION_GROUP_ID(false),

    APPLICATION_LISTENER(true),
    PROJECT_LISTENER(true),
    LISTENER_TOPIC(true);

    private final boolean myIsClass;

    RegistrationType(boolean isClass) {
      myIsClass = isClass;
    }

    boolean isClass() {
      return myIsClass;
    }
  }
}
