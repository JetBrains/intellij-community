/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilderUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IErrorCounterReparseableElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrBlockImpl;

/**
 * @author peter
 */
public abstract class GrCodeBlockElementType extends IErrorCounterReparseableElementType implements ICompositeElementType {

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
  public int getErrorsCount(final CharSequence seq, Language fileLanguage, final Project project) {
    return PsiBuilderUtil.hasProperBraceBalance(seq, new GroovyLexer(), GroovyTokenTypes.mLCURLY, GroovyTokenTypes.mRCURLY)
           ? NO_ERRORS
           : FATAL_ERROR;
  }
}
