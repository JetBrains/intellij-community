/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion.smartEnter;

import com.intellij.lang.SmartEnterProcessorWithFixers;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.OrderedSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers.*;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.processors.GroovyPlainEnterProcessor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.07.2008
 */
public class GroovySmartEnterProcessor extends SmartEnterProcessorWithFixers {
  public GroovySmartEnterProcessor() {
    final List<SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor>> ourFixers = Arrays.asList(
        new SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor>() {
          @Override
          public void apply(@NotNull Editor editor, @NotNull GroovySmartEnterProcessor processor, @NotNull PsiElement psiElement) {
            GrCatchClause catchClause = PsiTreeUtil.getParentOfType(psiElement, GrCatchClause.class);
            if (catchClause == null || catchClause.getBody() != null) return;
            if (!PsiTreeUtil.isAncestor(catchClause.getParameter(), psiElement, false)) return;
        
            final Document doc = editor.getDocument();
        
            PsiElement lBrace = catchClause.getLBrace();
            if (lBrace != null) return;
        
            PsiElement eltToInsertAfter = catchClause.getRParenth();
            String text = "{\n}";
            if (eltToInsertAfter == null) {
              eltToInsertAfter = catchClause.getParameter();
              text = "){\n}";
            }
            if (eltToInsertAfter != null) {
              doc.insertString(eltToInsertAfter.getTextRange().getEndOffset(), text);
            }
          }
        },
        new GrClassBodyFixer() ,
        new GrMissingIfStatement(),
        new GrIfConditionFixer(),
        new GrLiteralFixer(),
        new GrMethodCallFixer(),
        new GrMethodBodyFixer(),
        new GrMethodParametersFixer(),
        new GrWhileConditionFixer(),
        new GrWhileBodyFixer(),
        new GrForBodyFixer(),
        new GrSwitchBodyFixer(),
        new GrSynchronizedFixer(),
        new GrListFixer(),
        new GrMethodCallWithSingleClosureArgFixer()
      );
    addFixers(ourFixers.toArray(new Fixer[ourFixers.size()]));
    addEnterProcessors(new GroovyPlainEnterProcessor());
  }

  @Override
  protected void reformat(PsiElement atCaret) throws IncorrectOperationException {
    PsiElement parent = atCaret.getParent();
    if (parent instanceof GrCodeBlock) {
      final GrCodeBlock block = (GrCodeBlock) parent;
      if (block.getStatements().length > 0 && block.getStatements()[0] == atCaret) {
        atCaret = block;
      }
    } else if (parent instanceof GrForStatement || parent instanceof GrSwitchStatement) {
      atCaret = parent;
    }

    super.reformat(atCaret);
  }

  @Override
  protected void collectAllElements(@NotNull PsiElement atCaret, @NotNull OrderedSet<PsiElement> res, boolean recurse) {
    res.add(0, atCaret);
    if (doNotStepInto(atCaret)) {
      if (!recurse) return;
      recurse = false;
    }

    PsiElement parent = atCaret.getParent();
    if (atCaret instanceof GrClosableBlock && parent instanceof GrStringInjection && parent.getParent() instanceof GrString) {
      res.add(parent.getParent());
    }

    if (parent instanceof GrArgumentList) {
      res.add(parent.getParent());
    }

    //if (parent instanceof GrWhileStatement) {
    //  res.add(parent);
    //}

    final PsiElement[] children = getChildren(atCaret);

    for (PsiElement child : children) {
      collectAllElements(child, res, recurse);
    }
  }

  @Override
  public boolean doNotStepInto(PsiElement element) {
    return element instanceof PsiClass ||
           element instanceof GrCodeBlock ||
           element instanceof GrStatement && !(element instanceof GrExpression) ||
           PsiUtil.isExpressionStatement(element) ||
           element instanceof GrMethod;
  }

  @Override
  @Nullable
  protected PsiElement getStatementAtCaret(Editor editor, PsiFile psiFile) {
    PsiElement atCaret = super.getStatementAtCaret(editor, psiFile);

    if (atCaret instanceof PsiWhiteSpace) return null;

    while (atCaret != null && !PsiUtil.isExpressionStatement(atCaret)) {
      if (atCaret instanceof PsiMethod || atCaret instanceof GrDocComment || atCaret instanceof GrTypeDefinition) return atCaret;
      atCaret = atCaret.getParent();
    }

    return atCaret;
  }

  @Override
  protected void moveCaretInsideBracesIfAny(@NotNull final Editor editor, @NotNull final PsiFile file) throws IncorrectOperationException {
    int caretOffset = editor.getCaretModel().getOffset();
    final CharSequence chars = editor.getDocument().getCharsSequence();

    if (CharArrayUtil.regionMatches(chars, caretOffset, "{}")) {
      caretOffset += 2;
    } else if (CharArrayUtil.regionMatches(chars, caretOffset, "{\n}")) {
      caretOffset += 3;
    }

    caretOffset = CharArrayUtil.shiftBackward(chars, caretOffset - 1, " \t") + 1;

    if (CharArrayUtil.regionMatches(chars, caretOffset - "{}".length(), "{}") ||
        CharArrayUtil.regionMatches(chars, caretOffset - "{\n}".length(), "{\n}")) {
      commit(editor);
      final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(file.getProject());
      final boolean old = settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE;
      settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
      PsiElement elt = PsiTreeUtil.getParentOfType(file.findElementAt(caretOffset - 1), GrCodeBlock.class, GrTypeDefinitionBody.class);
      reformat(elt);
      settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = old;
      editor.getCaretModel().moveToOffset(caretOffset - 1);
    }
  }

  private static PsiElement[] getChildren(PsiElement element) {
    PsiElement psiChild = element.getFirstChild();
    if (psiChild == null) return PsiElement.EMPTY_ARRAY;

    List<PsiElement> result = new ArrayList<>();
    while (psiChild != null) {
      result.add(psiChild);

      psiChild = psiChild.getNextSibling();
    }
    return PsiUtilCore.toPsiElementArray(result);
  }
}
