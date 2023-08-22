// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.plugins.groovy.lang.parser.GroovyGeneratedParserUtils.adapt_builder_;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CODE_REFERENCE;

public class GroovyParser extends GroovyGeneratedParser {

  public boolean parseDeep() {
    return false;
  }

  //gsp directives, scriptlets and such
  protected boolean isExtendedSeparator(@Nullable final IElementType tokenType) {
    return false;
  }

  //gsp template statement, for example
  protected boolean parseExtendedStatement(PsiBuilder builder) {
    return false;
  }

  public void parseDocReference(PsiBuilder b) {
    b = adapt_builder_(CODE_REFERENCE, b, this, EXTENDS_SETS_);
    doc_reference(b, 0);
  }
}
