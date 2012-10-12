package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.utils.JavaStylePropertiesUtil;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

public class JavaStylePropertiesInvocationFixer implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return GroovyIntentionsBundle.message("java.style.properties.invocation.intention.name");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyIntentionsBundle.message("java.style.properties.invocation.intention.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement pparent = descriptor.getPsiElement().getParent().getParent();
    if (pparent instanceof GrMethodCall){
      JavaStylePropertiesUtil.fixJavaStyleProperty((GrMethodCall)pparent);
    }
  }
}
