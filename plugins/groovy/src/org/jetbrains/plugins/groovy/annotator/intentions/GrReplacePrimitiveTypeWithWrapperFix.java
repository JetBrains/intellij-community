/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author Max Medvedev
 */
public class GrReplacePrimitiveTypeWithWrapperFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(GrReplacePrimitiveTypeWithWrapperFix.class);

  private final GrTypeElement myTypeElement;
  private final String myBoxedName;

  public GrReplacePrimitiveTypeWithWrapperFix(GrTypeElement typeElement) {
    LOG.assertTrue(typeElement.isValid());
    myTypeElement = typeElement;

    final PsiType type = typeElement.getType();
    LOG.assertTrue(type instanceof PsiPrimitiveType);

    myBoxedName = ((PsiPrimitiveType)type).getBoxedType(typeElement).getClassName();
  }

  @NotNull
  @Override
  public String getText() {
    return GroovyIntentionsBundle.message("replace.with.wrapper", myBoxedName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyIntentionsBundle.message("replace.primitive.type.with.wrapper");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myTypeElement.isValid() && myTypeElement.getType() instanceof PsiPrimitiveType;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiType type = myTypeElement.getType();
    if (!(type instanceof PsiPrimitiveType)) return;

    final PsiClassType boxed = ((PsiPrimitiveType)type).getBoxedType(myTypeElement);
    final GrTypeElement newTypeElement = GroovyPsiElementFactory.getInstance(project).createTypeElement(boxed);

    final PsiElement replaced = myTypeElement.replace(newTypeElement);
    GrReferenceAdjuster.shortenReferences(replaced);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
