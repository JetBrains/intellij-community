// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.openapi.impl.RenameRefactoringImpl;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author Maxim.Medvedev
 */
public class RenameFileWithClassIntention extends Intention implements Consumer<GrTypeDefinition> {

  private String myNewFileName = null;

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    final PsiFile file = element.getContainingFile();
    new RenameRefactoringImpl(project, file, myNewFileName, true, true).run();
  }

  @NotNull
  @Override
  public String getText() {
    return GroovyBundle.message("rename.file.to.0", myNewFileName);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ClassNameDiffersFromFileNamePredicate(this);
  }

  @Override
  public void consume(GrTypeDefinition def) {
    final String name = def.getName();
    final PsiFile file = def.getContainingFile();
    myNewFileName = name + "." + FileUtilRt.getExtension(file.getName());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
