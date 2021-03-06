// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrDisjunctionTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author Max Medvedev
 */
public class GrRemoveExceptionFix implements IntentionAction {
  private final @IntentionName String myText;
  private final boolean myDisjunction;

  public GrRemoveExceptionFix(boolean isDisjunction) {
    myDisjunction = isDisjunction;
    if (isDisjunction) {
      myText = GroovyBundle.message("remove.exception");
    }
    else {
      myText = GroovyBundle.message("remove.catch.block");
    }
  }

  @NotNull
  @Override
  @IntentionName
  public String getText() {
    return myText;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("try.catch.fix");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myDisjunction && findTypeElementInDisjunction(editor, file) != null || !myDisjunction && findCatch(editor, file) != null;
  }

  @Nullable
  private static GrTypeElement findTypeElementInDisjunction(Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement at = file.findElementAt(offset);
    final GrDisjunctionTypeElement disjunction = PsiTreeUtil.getParentOfType(at, GrDisjunctionTypeElement.class);
    if (disjunction == null) return null;
    for (GrTypeElement element : disjunction.getTypeElements()) {
      if (element.getTextRange().contains(offset)) {
        return element;
      }
    }
    return null;
  }

  @Nullable
  private static GrCatchClause findCatch(Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement at = file.findElementAt(offset);
    return PsiTreeUtil.getParentOfType(at, GrCatchClause.class);
  }


  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (myDisjunction) {
      final GrTypeElement element = findTypeElementInDisjunction(editor, file);
      if (element != null) {
        element.delete();
      }
    }
    else {
      final GrCatchClause aCatch = findCatch(editor, file);
      if (aCatch != null) {
        aCatch.delete();
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
