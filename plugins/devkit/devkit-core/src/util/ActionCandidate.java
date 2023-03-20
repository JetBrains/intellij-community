// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.util;

import com.intellij.psi.SmartPointerManager;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;

import java.util.Objects;

public class ActionCandidate extends PointableCandidate {
  public ActionCandidate(ActionOrGroup actionOrGroup) {
    super(SmartPointerManager.createPointer(Objects.requireNonNull(actionOrGroup.getXmlTag())));
  }
}
