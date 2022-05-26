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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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

  @NotNull
  @Override
  public String getText() {
    return GroovyBundle.message("move.class.0.from.method", myClass.getName());
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("move.class.from.method.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myClass.isValid();
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

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        return myClass.isValid();
      }
    };
  }
}
