// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.groovydoc.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

/**
 * @author ilyas
 */
public class GroovyDocElementType extends IElementType implements IGroovyDocElementType {

  public GroovyDocElementType(@NotNull @NonNls String debugName) {
    super(debugName, GroovyLanguage.INSTANCE);
  }
}
