// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.util.xml.DomElement;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.Component;
import org.jetbrains.idea.devkit.dom.Listeners;

public class RegistrationEntry {

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
    ACTION(true, RegistrationDomType.ACTION_OR_GROUP),

    APPLICATION_COMPONENT(true, RegistrationDomType.COMPONENT),
    PROJECT_COMPONENT(true, RegistrationDomType.COMPONENT),
    MODULE_COMPONENT(true, RegistrationDomType.COMPONENT),
    COMPONENT_INTERFACE(true, RegistrationDomType.COMPONENT),

    ACTION_ID(false, RegistrationDomType.ACTION_OR_GROUP),
    ACTION_GROUP_ID(false, RegistrationDomType.ACTION_OR_GROUP),

    APPLICATION_LISTENER(true, RegistrationDomType.LISTENER),
    PROJECT_LISTENER(true, RegistrationDomType.LISTENER),
    LISTENER_TOPIC(true, RegistrationDomType.LISTENER);

    private final boolean myIsClass;
    private final RegistrationDomType myRegistrationDomType;

    RegistrationType(boolean isClass, RegistrationDomType registrationDomType) {
      myIsClass = isClass;
      myRegistrationDomType = registrationDomType;
    }

    boolean isClass() {
      return myIsClass;
    }

    RegistrationDomType getRegistrationDomType() {
      return myRegistrationDomType;
    }
  }

  enum RegistrationDomType {
    ACTION_OR_GROUP(ActionOrGroup.class, false),
    COMPONENT(Component.class, true),
    LISTENER(Listeners.Listener.class, true);

    private final Class<? extends DomElement> myDomClass;
    private final boolean myUseParentDom;

    RegistrationDomType(Class<? extends DomElement> domClazz, boolean useParentDom) {
      myDomClass = domClazz;
      myUseParentDom = useParentDom;
    }

    Class<? extends DomElement> getDomClass() {
      return myDomClass;
    }

    boolean isUseParentDom() {
      return myUseParentDom;
    }
  }
}
