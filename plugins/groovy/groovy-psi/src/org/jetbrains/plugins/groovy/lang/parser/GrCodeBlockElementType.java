// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilderUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IReparseableElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrBlockImpl;

/**
 * @author peter
 */
public abstract class GrCodeBlockElementType extends IReparseableElementType implements ICompositeElementType {

  protected GrCodeBlockElementType(String debugName) {
    super(debugName, GroovyLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return createNode(null);
  }

  @Override
  @NotNull
  public abstract GrBlockImpl createNode(final CharSequence text);

  @Override
  public boolean isParsable(@NotNull CharSequence buffer, @NotNull Language fileLanguage, @NotNull Project project) {
    return PsiBuilderUtil.hasProperBraceBalance(buffer, new GroovyLexer(), GroovyTokenTypes.mLCURLY, GroovyTokenTypes.mRCURLY);
  }
}
