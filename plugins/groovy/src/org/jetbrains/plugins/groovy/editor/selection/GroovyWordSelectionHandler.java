/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.editor.selection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GroovyWordSelectionHandler extends ExtendWordSelectionHandlerBase {
  private static final Logger LOG = Logger.getInstance(GroovyWordSelectionHandler.class);

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return (e instanceof GroovyPsiElement || e.getLanguage() == GroovyLanguage.INSTANCE) &&
           !(e.getNode().getElementType() == GroovyTokenTypes.mDOLLAR);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    final TextRange originalRange = e.getTextRange();
    LOG.assertTrue(originalRange.getEndOffset() <= editorText.length(), getClass() + "; " + e);

    List<TextRange> ranges;
    if (isSeparateStatement(e)) {
      ranges = ExtendWordSelectionHandlerBase.expandToWholeLine(editorText, originalRange, true);
      if (ranges.size() == 1 && ranges.contains(originalRange)) {
        ranges = ExtendWordSelectionHandlerBase.expandToWholeLine(editorText, originalRange, false);
      }
    }
    else {
      ranges = new ArrayList<>();
      ranges.add(e.getTextRange());
    }

    SelectWordUtil.addWordOrLexemeSelection(editor.getSettings().isCamelWords(), editor, cursorOffset, ranges);

    return ranges;
  }

  private static boolean isSeparateStatement(PsiElement e) {
    return
      e instanceof LeafPsiElement ||
      e instanceof GrExpression && PsiUtil.isExpressionStatement(e) ||
      e instanceof GrVariableDeclaration ||
      e instanceof GrStatement && !(e instanceof GrExpression) ||
      e instanceof GrMethod ||
      e instanceof GrClassInitializer ||
      e instanceof GrImportStatement ||
      e instanceof GrPackageDefinition ||
      e instanceof GrOpenBlock ||
      e instanceof GrTypeDefinition && !((GrTypeDefinition)e).isAnonymous()
      ;
  }
}
