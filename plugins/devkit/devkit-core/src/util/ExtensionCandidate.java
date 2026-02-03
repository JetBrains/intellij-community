// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public class ExtensionCandidate extends PointableCandidate {
  public ExtensionCandidate(@NotNull SmartPsiElementPointer<XmlTag> pointer) {
    super(pointer);
  }
}
