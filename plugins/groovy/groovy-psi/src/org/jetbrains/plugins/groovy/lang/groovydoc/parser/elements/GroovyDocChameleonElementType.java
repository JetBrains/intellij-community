// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.groovydoc.parser.elements;

import com.intellij.lang.Language;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

public abstract class GroovyDocChameleonElementType extends ILazyParseableElementType {
  public GroovyDocChameleonElementType(@NonNls String debugName) {
    super(debugName);
  }

  @Override
  public @NotNull Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }

}
