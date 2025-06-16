// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author Max Medvedev
 */
public class GrMoveClassToCorrectPlaceFix extends Intention {
  private static final Logger LOG = Logger.getInstance(GrMoveClassToCorrectPlaceFix.class);

  private final GrTypeDefinition myClass;

  public GrMoveClassToCorrectPlaceFix(GrTypeDefinition clazz) {
    myClass = clazz;
    LOG.assertTrue(!myClass.isAnonymous());
  }

  @Override
  public @NotNull String getText() {
    return GroovyBundle.message("move.class.0.from.method", myClass.getName());
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("move.class.from.method.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return myClass.isValid();
  }

  //@Override
  //public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
  //  GrTypeDefinition copy = PsiTreeUtil.findSameElementInCopy(myClass, target);
  //  return new GrMoveClassToCorrectPlaceFix(copy);
  //}


  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    final GrTypeDefinition containingClass = PsiTreeUtil.getParentOfType(myClass, GrTypeDefinition.class);
    PsiNamedElement target;
    if (containingClass != null) {
      target = containingClass;
    }
    else {
      target = myClass.getContainingFile();
    }
    return IntentionPreviewInfo.movePsi(myClass, target);
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    final GrTypeDefinition containingClass = PsiTreeUtil.getParentOfType(myClass, GrTypeDefinition.class);
    if (containingClass != null) {
      containingClass.add(myClass);
    }
    else {
      final PsiFile containingFile = myClass.getContainingFile();
      final PsiElement added = containingFile.add(myClass);
      final PsiElement prevSibling = added.getPrevSibling();
      if (prevSibling != null && prevSibling.getNode().getElementType() != GroovyTokenTypes.mNLS) {
        containingFile.getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", added.getNode());
      }
    }

    myClass.delete();
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        return myClass.isValid();
      }
    };
  }
}
