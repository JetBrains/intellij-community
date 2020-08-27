// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;

public class ReplaceWithImportFix extends GroovyFix {
  private static final Logger LOG = Logger.getInstance(UnnecessaryQualifiedReferenceInspection.class);

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiElement startElement = descriptor.getStartElement();
    LOG.assertTrue(startElement instanceof GrReferenceElement<?>);
    GrReferenceAdjuster.shortenReference((GrQualifiedReference<?>)startElement);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("replace.qualified.name.with.import");
  }
}
