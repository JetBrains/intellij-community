// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author Max Medvedev
 */
public class GrReplacePrimitiveTypeWithWrapperFix extends PsiUpdateModCommandQuickFix {
  private static final Logger LOG = Logger.getInstance(GrReplacePrimitiveTypeWithWrapperFix.class);

  private final String myBoxedName;

  public GrReplacePrimitiveTypeWithWrapperFix(GrTypeElement typeElement) {
    LOG.assertTrue(typeElement.isValid());

    final PsiType type = typeElement.getType();
    LOG.assertTrue(type instanceof PsiPrimitiveType);

    myBoxedName = ((PsiPrimitiveType)type).getBoxedTypeName();
  }

  @Override
  public @NotNull String getName() {
    return GroovyBundle.message("replace.with.wrapper", myBoxedName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("replace.primitive.type.with.wrapper");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
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
