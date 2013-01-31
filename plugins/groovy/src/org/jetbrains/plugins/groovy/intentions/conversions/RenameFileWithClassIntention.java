/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.openapi.impl.RenameRefactoringImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author Maxim.Medvedev
 */
public class RenameFileWithClassIntention extends Intention {

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    final GrTypeDefinition psiClass = (GrTypeDefinition)element.getParent();
    final String name = psiClass.getName();

    final PsiFile file = psiClass.getContainingFile();
    final String newFileName = name + "." + FileUtilRt.getExtension(file.getName());
    new RenameRefactoringImpl(project, file, newFileName, true, true).run();
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ClassNameDiffersFromFileNamePredicate();
  }
}
