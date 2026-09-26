// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.util;

import com.intellij.psi.SmartPointerManager;
import org.jetbrains.idea.devkit.dom.Listeners;

import java.util.Objects;

public class ListenerCandidate extends PointableCandidate {
  public ListenerCandidate(Listeners.Listener listener) {
    super(SmartPointerManager.createPointer(Objects.requireNonNull(listener.getXmlTag())));
  }
}
