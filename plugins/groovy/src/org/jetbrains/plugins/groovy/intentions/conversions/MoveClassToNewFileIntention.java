// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author Maxim.Medvedev
 */
public class MoveClassToNewFileIntention extends Intention {

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    final GrTypeDefinition psiClass = (GrTypeDefinition)element.getParent();
    final String name = psiClass.getName();

    final PsiFile file = psiClass.getContainingFile();
    final String fileExtension = FileUtilRt.getExtension(file.getName());
    final String newFileName = name + "." + fileExtension;
    final PsiDirectory dir = file.getParent();
    if (dir != null) {
      if (dir.findFile(newFileName) != null) {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          final String message = GroovyBundle.message("file.exists", newFileName, dir.getName());
          CommonRefactoringUtil.showErrorHint(project, editor, message, getFamilyName(), null);
        }
        return;
      }
    }

    final GroovyFile newFile = (GroovyFile)GroovyTemplatesFactory.createFromTemplate(dir, name, newFileName, GroovyTemplates.GROOVY_CLASS,
                                                                                     true);
    WriteAction.run(() -> {
      GrTypeDefinition template = newFile.getTypeDefinitions()[0];
      PsiElement newClass = template.replace(psiClass);
      GrDocComment docComment = psiClass.getDocComment();
      if (newClass instanceof GrTypeDefinition && docComment != null) {
        GrDocComment newDoc = ((GrTypeDefinition)newClass).getDocComment();
        if (newDoc != null) {
          newDoc.replace(docComment);
        }
        else {
          PsiElement parent = newClass.getParent();
          parent.addBefore(docComment, psiClass);
          parent.getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", psiClass.getNode());
        }
        docComment.delete();
      }
      psiClass.delete();
      IntentionUtils.positionCursor(project, newClass.getContainingFile(), newClass.getNavigationElement());
    });
  }


  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ClassNameDiffersFromFileNamePredicate(true);
  }
}
