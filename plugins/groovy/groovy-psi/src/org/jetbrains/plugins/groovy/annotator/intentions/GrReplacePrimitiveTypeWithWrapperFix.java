// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author Max Medvedev
 */
public class GrReplacePrimitiveTypeWithWrapperFix extends GroovyFix {
  private static final Logger LOG = Logger.getInstance(GrReplacePrimitiveTypeWithWrapperFix.class);

  private final String myBoxedName;

  public GrReplacePrimitiveTypeWithWrapperFix(GrTypeElement typeElement) {
    LOG.assertTrue(typeElement.isValid());

    final PsiType type = typeElement.getType();
    LOG.assertTrue(type instanceof PsiPrimitiveType);

    myBoxedName = ((PsiPrimitiveType)type).getBoxedTypeName();
  }

  @NotNull
  @Override
  public String getName() {
    return GroovyBundle.message("replace.with.wrapper", myBoxedName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("replace.primitive.type.with.wrapper");
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiElement element = descriptor.getPsiElement();
    assert element instanceof GrTypeElement : element;

    GrTypeElement typeElement = (GrTypeElement)element;
    final PsiType type = typeElement.getType();
    if (!(type instanceof PsiPrimitiveType)) return;

    final PsiClassType boxed = ((PsiPrimitiveType)type).getBoxedType(typeElement);
    if (boxed == null) return;
    final GrTypeElement newTypeElement = GroovyPsiElementFactory.getInstance(project).createTypeElement(boxed);

    final PsiElement replaced = typeElement.replace(newTypeElement);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
  }
}
