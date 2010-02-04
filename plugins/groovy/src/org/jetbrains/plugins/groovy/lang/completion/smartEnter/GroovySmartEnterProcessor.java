/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.editorActions.smartEnter.EnterProcessor;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers.*;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.processors.GroovyPlainEnterProcessor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.07.2008
 */
public class GroovySmartEnterProcessor extends SmartEnterProcessor {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor");

  private static final GrFixer[] ourFixers;
  private static final EnterProcessor[] ourEnterProcessors;

  static {
    final List<GrFixer> fixers = new ArrayList<GrFixer>();
    fixers.add(new GrMissingIfStatement());
    fixers.add(new GrIfConditionFixer());
    fixers.add(new GrLiteralFixer());
    fixers.add(new GrMethodCallFixer());
    fixers.add(new GrMethodBodyFixer());
    fixers.add(new GrMethodParametersFixer());
    fixers.add(new GrWhileConditionFixer());
    fixers.add(new GrWhileBodyFixer());
    fixers.add(new GrForBodyFixer());

    ourFixers = fixers.toArray(new GrFixer[fixers.size()]);

    List<EnterProcessor> processors = new ArrayList<EnterProcessor>();
    processors.add(new GroovyPlainEnterProcessor());
    ourEnterProcessors = processors.toArray(new EnterProcessor[processors.size()]);
  }

  private int myFirstErrorOffset = Integer.MAX_VALUE;
  private static final int MAX_ATTEMPTS = 20;
  private static final Key<Long> SMART_ENTER_TIMESTAMP = Key.create("smartEnterOriginalTimestamp");

  public static class TooManyAttemptsException extends Exception {
  }

  public boolean process(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile psiFile) {
    final Document document = editor.getDocument();
    final String textForRollback = document.getText();
    try {
      editor.putUserData(SMART_ENTER_TIMESTAMP, editor.getDocument().getModificationStamp());
      myFirstErrorOffset = Integer.MAX_VALUE;
      process(project, editor, psiFile, 0);
    }
    catch (TooManyAttemptsException e) {
      document.replaceString(0, document.getTextLength(), textForRollback);
    } finally {
      editor.putUserData(SMART_ENTER_TIMESTAMP, null);
    }
    return true;
  }


  private void process(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file, final int attempt) throws TooManyAttemptsException {
    if (attempt > MAX_ATTEMPTS) throw new TooManyAttemptsException();

    try {
      commit(editor);
       if (myFirstErrorOffset != Integer.MAX_VALUE) {
        editor.getCaretModel().moveToOffset(myFirstErrorOffset);
      }

      myFirstErrorOffset = Integer.MAX_VALUE;

      PsiElement atCaret = getStatementAtCaret(editor, file);
      if (atCaret == null) {
        if (!new GroovyCommentBreakerEnterProcessor().doEnter(editor, file, false)) {
          plainEnter(editor);
        }
        return;
      }

      List<PsiElement> queue = new ArrayList<PsiElement>();
      collectAllElements(atCaret, queue, true);
      queue.add(atCaret);

      for (PsiElement psiElement : queue) {
        for (GrFixer fixer : ourFixers) {
          fixer.apply(editor, this, psiElement);
          if (LookupManager.getInstance(project).getActiveLookup() != null) {
            return;
          }
          if (isUncommited(project) || !psiElement.isValid()) {
            moveCaretInsideBracesIfAny(editor, file);
            process(project, editor, file, attempt + 1);
            return;
          }
        }
      }

      doEnter(atCaret, editor);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  protected void reformat(PsiElement atCaret) throws IncorrectOperationException {
    PsiElement parent = atCaret.getParent();
    if (parent instanceof GrCodeBlock) {
      final GrCodeBlock block = (GrCodeBlock) parent;
      if (block.getStatements().length > 0 && block.getStatements()[0] == atCaret) {
        atCaret = block;
      }
    } else if (parent instanceof GrForStatement) {
      atCaret = parent;
    }

    super.reformat(atCaret);
  }


  private void doEnter(PsiElement atCaret, Editor editor) throws IncorrectOperationException {
    final PsiFile psiFile = atCaret.getContainingFile();

    final RangeMarker rangeMarker = createRangeMarker(atCaret);
    if (myFirstErrorOffset != Integer.MAX_VALUE) {
      editor.getCaretModel().moveToOffset(myFirstErrorOffset);
      reformat(atCaret);
      return;
    }

    reformat(atCaret);
    commit(editor);

    atCaret = GroovyRefactoringUtil.findElementInRange(((GroovyFileBase) psiFile), rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), atCaret.getClass());

//    atCaret = CodeInsightUtil.findElementInRange(psiFile, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), atCaret.getClass());


    for (EnterProcessor processor : ourEnterProcessors) {
      if (atCaret == null) {
        // Can't restore element at caret after enter processor execution!
        break;
      }

      if (processor.doEnter(editor, atCaret, isModified(editor))) return;
    }

    if (!isModified(editor)) {
      plainEnter(editor);
    } else {
      if (myFirstErrorOffset == Integer.MAX_VALUE) {
        editor.getCaretModel().moveToOffset(rangeMarker.getEndOffset());
      } else {
        editor.getCaretModel().moveToOffset(myFirstErrorOffset);
      }
    }
  }

  private static void collectAllElements(PsiElement atCaret, List<PsiElement> res, boolean recurse) {
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
      if (atCaret instanceof GrStatement && child instanceof GrStatement) continue;
      collectAllElements(child, res, recurse);
    }
  }

  private static boolean doNotStepInto(PsiElement element) {
    return element instanceof PsiClass || element instanceof GrCodeBlock || element instanceof GrStatement || element instanceof GrMethod;
  }

  @Nullable
  protected PsiElement getStatementAtCaret(Editor editor, PsiFile psiFile) {
    final PsiElement atCaret = super.getStatementAtCaret(editor, psiFile);

    if (atCaret instanceof PsiWhiteSpace) return null;
    if (atCaret == null) return null;

    final GrCodeBlock codeBlock = PsiTreeUtil.getParentOfType(atCaret, GrCodeBlock.class, false, GrControlStatement.class);
    if (codeBlock != null) {
      for (GrStatement statement : codeBlock.getStatements()) {
        if (PsiTreeUtil.isAncestor(statement, atCaret, true)) {
          return statement;
        }
      }
    }

    PsiElement statementAtCaret = PsiTreeUtil.getParentOfType(atCaret,
            GrStatement.class,
            GrCodeBlock.class,
            PsiMember.class,
            GrDocComment.class
    );

    if (statementAtCaret instanceof GrBlockStatement) return null;
    if (statementAtCaret == null) return null;

    GrControlStatement controlStatement = PsiTreeUtil.getParentOfType(statementAtCaret, GrControlStatement.class, false);

    if (controlStatement != null && !PsiTreeUtil.hasErrorElements(statementAtCaret)) {
      return controlStatement;
    }

    return statementAtCaret instanceof GrStatement ||
            statementAtCaret instanceof GrMember
            ? statementAtCaret
            : null;
  }

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
      PsiElement elt = PsiTreeUtil.getParentOfType(file.findElementAt(caretOffset - 1), GrCodeBlock.class);
      reformat(elt);
      settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = old;
      editor.getCaretModel().moveToOffset(caretOffset - 1);
    }
  }

  public void registerUnresolvedError(int offset) {
    if (myFirstErrorOffset > offset) {
      myFirstErrorOffset = offset;
    }
  }

  protected static void plainEnter(@NotNull final Editor editor) {
    getEnterHandler().execute(editor, ((EditorEx) editor).getDataContext());
  }

  protected static EditorActionHandler getEnterHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
  }

  protected static boolean isModified(@NotNull final Editor editor) {
    final Long timestamp = editor.getUserData(SMART_ENTER_TIMESTAMP);
    return editor.getDocument().getModificationStamp() != timestamp.longValue();
  }


  private static PsiElement[] getChildren(PsiElement element) {
    PsiElement psiChild = element.getFirstChild();
    if (psiChild == null) return new PsiElement[0];

    List<PsiElement> result = new ArrayList<PsiElement>();
    while (psiChild != null) {
      result.add(psiChild);

      psiChild = psiChild.getNextSibling();
    }
    return result.toArray(new PsiElement[result.size()]);
  }
}
