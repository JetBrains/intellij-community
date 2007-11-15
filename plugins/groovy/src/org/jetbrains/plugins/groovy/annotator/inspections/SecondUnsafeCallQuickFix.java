package org.jetbrains.plugins.groovy.annotator.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2007
 */
public class SecondUnsafeCallQuickFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.annotator.inspections.SecondUnsafeCallQuickFix");

  @NotNull
  public String getName() {
    return GroovyInspectionBundle.message("second.unsafe.call");
  }

  @NotNull
  public String getFamilyName() {
    return GroovyInspectionBundle.message("second.unsafe.call");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof GrReferenceExpression)) return;

    final PsiElement newDot = GroovyElementFactory.getInstance(project).createDotToken(GroovyElementTypes.mOPTIONAL_DOT.toString());
    ((GrReferenceExpression) element).replaceDotToken(newDot);
  }
}
