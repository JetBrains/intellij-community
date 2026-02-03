// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.util;

import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public abstract class PointableCandidate {
  public final @NotNull SmartPsiElementPointer<XmlTag> pointer;

  PointableCandidate(@NotNull SmartPsiElementPointer<XmlTag> pointer) {
    this.pointer = pointer;
  }
}
